# F9 — Política temporal y zona horaria por Store

Fecha: 2026-07-23  
Rama: `main`  
Alcance: hallazgo HIGH de timezone por Store de la Fase 9.

## 1. Semántica temporal anterior

La aplicación configuraba un único `Clock` mediante
`app.business-zone`, con valor por defecto `Europe/Madrid`. Los rangos
recibidos como `LocalDate` se convertían directamente con
`from.atStartOfDay()` y `to.plusDays(1).atStartOfDay()`, sin consultar la
timezone de la Store activa.

Los instantes operativos se representaban como `LocalDateTime` y se
persistían en PostgreSQL como `timestamp without time zone`. Hibernate
persistía sus campos sin offset ni zona.

No existe evidencia suficiente para atribuir una única zona histórica a
todos los registros:

- varios servicios usaban `LocalDateTime.now(clock)`, normalmente con el
  reloj global `Europe/Madrid`;
- `Ticket`, `TicketLine`, `AuditEvent` e `IdempotencyRecord` contienen o
  contenían defaults `LocalDateTime.now()` dependientes de la zona de la JVM;
- algunas migraciones incorporaron defaults o backfills PostgreSQL con
  `now()`, dependientes de la sesión de base de datos;
- las columnas no conservan offset ni zona.

Por tanto, un timestamp histórico naïve no debe reinterpretarse
automáticamente como UTC.

## 2. Inventario y clasificación

| Área / campos | Tipo actual | Clasificación | Observación |
|---|---|---|---|
| Ticket `createdAt`, `updatedAt`, `paidAt`, `cancelledAt` | `LocalDateTime` | instante físico e histórico mostrado | `paidAt` alimenta historial/reportes; `cancelledAt` alimenta el count corregido |
| TicketLine `createdAt`, `discountAppliedAt` | `LocalDateTime` | instante físico e histórico | snapshots conservan el momento operacional |
| Payment `paidAt`, `createdAt` | `LocalDateTime` | instante físico e histórico | reportes por método usan `paidAt` |
| CashSession `openedAt`, `createdAt`, `closedAt` | `LocalDateTime` | instante físico e histórico | apertura/cierre de caja |
| CashMovement `performedAt`, `createdAt` | `LocalDateTime` | instante físico e histórico | movimientos de caja |
| CashRegister/Store `createdAt`, `updatedAt` | `LocalDateTime` | instante físico administrativo | no define cortes F9 |
| AuditEvent `occurredAt` | `LocalDateTime` | auditoría técnica/histórica | filtros públicos aceptan fecha/hora naïve |
| IdempotencyRecord `createdAt`, `completedAt`, `expiresAt` | `LocalDateTime` | expiración técnica | scheduler elimina por reloj técnico |
| UserStoreAccess `assignedAt` | `LocalDateTime` | histórico administrativo | no define día comercial |
| API de reportes `from`, `to` | `LocalDate` | fecha de negocio | `from` inclusivo, `to` inclusivo |
| DailySalesSummary `date` | `LocalDate` | fecha sin hora de negocio | ahora deriva de la timezone de Store |
| `ApiError.timestamp` | `Instant` | instante técnico | ya era UTC/offset-independent |
| numeración comercial | secuencia por Store | no temporal | no depende del día |
| jobs | idempotency cleanup | expiración técnica | no existe job de cierre diario |

Las migraciones V5.x, V6.x y V8.x confirman columnas
`timestamp without time zone` para auditoría, descuentos, pagos, cajas,
sesiones, movimientos, Stores y accesos.

## 3. Estrategia seleccionada

**Estrategia B — compatibilidad temporal controlada.**

Se mantienen `LocalDateTime`, los tipos PostgreSQL actuales y los DTO
existentes. La zona global `app.business-zone` se declara como zona de
representación de almacenamiento para los flujos compatibles. Cada límite
de negocio se crea primero en `Store.timezone`, se convierte a `Instant` y
después a `LocalDateTime` en la zona de almacenamiento antes de consultar.

Equivalencia:

```text
Store local date
→ atStartOfDay(Store.timezone)
→ Instant
→ LocalDateTime(app.business-zone)
→ query sobre timestamp without time zone
```

## 4. Justificación

La estrategia A (`Instant` + `timestamp with time zone`) es el objetivo
arquitectónico futuro, pero convertir ahora los datos históricos exigiría
conocer la zona original de cada fila. Esa información no está persistida
y los distintos orígenes temporales impiden inferirla de forma segura.

La estrategia B:

- corrige los límites de día comercial por Store;
- respeta días de 23, 24 y 25 horas;
- mantiene frontend, DTO, precisión y formato actuales;
- no reinterpreta ni sobrescribe históricos;
- permite una migración posterior cuando se defina y valide la procedencia
  temporal histórica.

## 5. Timezone de almacenamiento y de negocio

- representación compatible de almacenamiento:
  `app.business-zone`, por defecto `Europe/Madrid`;
- timezone de negocio: `ActiveStoreContext.requireStore().timezone`;
- fuente de tenant: usuario autenticado;
- fuente de Store: Store activa autenticada;
- no se acepta timezone ni Store desde los parámetros de reportes.

Una timezone de Store inválida provoca `INVALID_STORE_TIMEZONE`. No existe
fallback silencioso.

## 6. Componente central

Se creó `BusinessTimeService`, responsable de:

- resolver y validar `Store.timezone`;
- construir `StoreDateRange(fromInclusive, toExclusive, storeZone)`;
- convertir límites de Store a la representación de almacenamiento;
- convertir un timestamp almacenado a fecha local de Store;
- generar timestamps de escritura a partir de `clock.instant()`, sin usar la
  zona del `Clock` como día comercial.

## 7. Endpoints corregidos

Sin cambiar rutas, parámetros, roles, respuestas ni códigos HTTP:

- `GET /api/tickets/paid`
- `GET /api/tickets/paid/total`
- `GET /api/tickets/paid/payment-summary`
- `GET /api/tickets/paid/user-summary`
- `GET /api/tickets/paid/product-summary`
- `GET /api/tickets/paid/daily-summary`
- `GET /api/tickets/paid/average-ticket`
- `GET /api/tickets/cash-closing`

También se corrigió la variante interna no paginada de tickets pagados por
rango. Conserva `tenantId + storeId + status + [from,toExclusive)`.

Los reportes administrativos de sesiones de caja existentes no reciben
rangos de `LocalDate`; sus agregados son por `cashSessionId`, por lo que no
requerían conversión de día.

## 8. Escrituras operativas

Las nuevas escrituras de tickets y caja usan
`BusinessTimeService.nowForStorage()`:

- creación y pago de Ticket;
- creación de TicketLine;
- cancelación y aplicación de descuento;
- Payment asociado al pago;
- creación/actualización de CashRegister;
- apertura/cierre de CashSession;
- creación de CashMovement.

Auditoría e idempotencia permanecen clasificadas como tiempos técnicos. No
se cambió su contrato ni su política de expiración en este alcance.

## 9. Daily summary

El resumen diario ya no usa `ticket.getPaidAt().toLocalDate()` directamente.
Convierte el timestamp naïve desde la zona de almacenamiento al instante y
después a `Store.timezone`. El mismo instante puede pertenecer al 23 de julio
en Madrid y al 22 de julio en Lima.

## 10. Entidades y columnas modificadas

Ninguna entidad ni columna cambió de tipo.

- Java continúa usando `LocalDateTime`;
- PostgreSQL continúa usando `timestamp without time zone`;
- no se modificó nullability, índices, constraints ni precisión;
- DTO JSON continúa serializando los mismos tipos y campos.

`cash-closing` cuenta cancelaciones por `cancelledAt`, el timestamp
semánticamente correcto, en lugar de `updatedAt`.

## 11. Flyway y datos históricos

No se creó ni modificó una migración Flyway.

Motivo: no hay cambio de esquema y una conversión histórica a UTC sería
ambigua. No se ejecutaron `CAST`, `AT TIME ZONE`, updates ni backfills.

Camino posterior a UTC:

1. identificar o acordar la zona histórica por origen/periodo;
2. respaldar y validar muestras;
3. crear una migración nueva posterior a V9.1 con `AT TIME ZONE` explícito;
4. migrar entidades a `Instant`;
5. validar con PostgreSQL/Testcontainers y datos previos;
6. mantener mapping HTTP explícito.

## 12. Compatibilidad HTTP

No cambiaron:

- nombres de campos;
- rutas;
- roles;
- códigos HTTP;
- parámetros `LocalDate`;
- tipos de fecha/hora de los DTO;
- precisión observable.

`from` continúa inclusivo, `to` continúa inclusivo en API y el límite
interno superior continúa exclusivo.

## 13. Tests añadidos

`BusinessTimeServiceTest` añade 7 tests:

1. mismo instante con fechas locales distintas en Madrid y Lima;
2. inicio inclusivo y siguiente inicio exclusivo;
3. DST de primavera Madrid: 23 horas;
4. DST de otoño Madrid: 25 horas;
5. Lima: días de 24 horas en ambas fechas;
6. escritura basada en `Clock.instant()`, no en su zona;
7. timezone inválida sin fallback.

`TicketStoreScopeRepositoryTest` pasa de 3 a 4 tests y añade:

- inclusión exacta de `from`;
- inclusión de un ticket un segundo antes del final;
- exclusión de un ticket exactamente en `toExclusive`;
- exclusión del segundo anterior a `from`.

La prueba JPA existente conserva cobertura end-to-end de historial pagado,
total, payment summary, user summary, product summary, daily summary,
average-ticket, cash-closing, dos Stores y dos tenants.

H2 valida JPQL, derivación de repositorios, wiring y límites, pero no
sustituye PostgreSQL para tipos temporales o Flyway.

## 14. Resultados compile/test/verify

| Verificación | Resultado |
|---|---|
| suite específica | PASS; 11 tests |
| `.\mvnw.cmd clean compile` | BUILD SUCCESS; 200 fuentes |
| `.\mvnw.cmd test` | BUILD SUCCESS; 29 tests, 0 fallos/errores/skip |
| `.\mvnw.cmd clean verify` | BUILD SUCCESS; 29 tests; JAR generado |

Warning preexistente: `AuthService` usa/sobrescribe una API deprecada.

## 15. PostgreSQL

No ejecutado:

- `psql` no está disponible;
- no existen `DB_URL`, `DB_USERNAME` ni `DB_PASSWORD`;
- Docker CLI existe, pero el daemon de Docker Desktop no está activo;
- no fue posible ejecutar Testcontainers, Flyway sobre base vacía/con datos
  ni `ddl-auto=validate` contra PostgreSQL.

No se presenta H2 como sustituto de esta validación.

## 16. Smoke y regresión

No ejecutados en esta sesión:

- API sin listener en 8080;
- sin `psql`, conexión ni credenciales.

Pendientes:

- smoke específico timezone;
- smoke F9;
- regresión F8.

La evidencia documental previa de 79 PASS F9 y 48/48 F8 no se considera
revalidada después de este cambio. No se dejó backend activo ni logs en el
repositorio.

## 17. Archivos de este bloque

- `src/main/java/com/harbeyescala/api_apuntalo/service/BusinessTimeService.java`
- `src/main/java/com/harbeyescala/api_apuntalo/service/TicketService.java`
- `src/main/java/com/harbeyescala/api_apuntalo/service/CashRegisterService.java`
- `src/main/java/com/harbeyescala/api_apuntalo/service/CashSessionService.java`
- `src/main/java/com/harbeyescala/api_apuntalo/service/CashSessionOperationService.java`
- `src/main/java/com/harbeyescala/api_apuntalo/repository/TicketRepository.java`
- `src/test/java/com/harbeyescala/api_apuntalo/service/BusinessTimeServiceTest.java`
- `src/test/java/com/harbeyescala/api_apuntalo/repository/TicketStoreScopeRepositoryTest.java`
- `F9_STORE_TIMEZONE_REPORT.md`

Se preservaron los cambios e informes anteriores del workspace.

## 18. Riesgos y veredicto

Riesgos restantes:

- validar contra PostgreSQL real y Flyway;
- repetir smoke timezone/F9 y regresión F8;
- los históricos naïve pueden proceder de zonas distintas; no se
  reinterpretaron;
- la estrategia B es deuda controlada hasta una migración segura a UTC;
- timestamps técnicos/administrativos fuera de los cortes F9 conservan su
  contrato naïve;
- el resumen diario continúa agregando en memoria para rangos grandes.

**GO condicionado para el bloque timezone en compatibilidad controlada.**

La lógica de negocio por Store, límites exclusivos, DST y agrupamiento diario
queda corregida y cubierta por tests. El GO definitivo de persistencia
temporal requiere PostgreSQL/Flyway y smoke real.

No se añadieron filtros nuevos, no se implementó Fase 10 y no se hizo commit
ni push.
