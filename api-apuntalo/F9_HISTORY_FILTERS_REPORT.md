# F9 — Filtros de historial y rendimiento controlado

Fecha: 2026-07-23  
Rama: `main`  
Alcance: pendientes MEDIUM de historial pagado y límites de rango.

## 1. Situación anterior

`GET /api/tickets/paid` aceptaba únicamente `from`, `to`, `page` y
`size`. La consulta ya conservaba tenant, Store activa, estado PAID, rango
Store-aware y paginación en BD, pero no permitía filtrar por método de pago,
usuario pagador, mesa o número comercial.

Los ocho endpoints de historial/reportes con fechas no limitaban la amplitud
del rango. En particular, daily summary cargaba todos los tickets
Store-scoped y agregaba en memoria.

## 2. Filtros añadidos

Parámetros opcionales compatibles en `GET /api/tickets/paid`:

- `paymentMethod=CASH|CARD|MIXED`;
- `userId`;
- `mesaId`;
- `commercialNumber`.

No se aceptan `tenantId`, `negocioId`, `storeId` ni timezone. Tenant y Store
continúan derivados del principal autenticado y `ActiveStoreContext`.

## 3. Semántica

### paymentMethod

Se filtra por `Ticket.paymentMethod`, resumen persistido y autoritativo del
pago:

- CASH: ticket pagado con componente efectivo único;
- CARD: ticket pagado con componente tarjeta único;
- MIXED: el flujo de pago validó y persistió componentes CASH y CARD, y
  asignó `Ticket.paymentMethod=MIXED`.

Las filas `Payment` conservan los componentes individuales. No se infiere
MIXED desde texto o DTO.

### userId

Filtra por `Ticket.paidBy.id`, que representa al usuario que realizó el pago.
No se hace carga global previa del usuario. Un usuario de otro tenant o Store
produce una página vacía por el scope obligatorio de la misma query.

### mesaId

Filtra la relación `Ticket.mesa.id` persistida al finalizar la operación. El
modelo no dispone de snapshot separado del número de mesa; si la mesa cambió
antes del pago, la relación final persistida es la semántica histórica
disponible. No se ejecuta `findById` global.

### commercialNumber

El parámetro es `Long` porque la BD almacena `bigint`. Un valor como `000042`
se convierte de forma compatible a `42`; el formato visual de seis dígitos
no se compara en SQL. Se rechazan cero y negativos. Texto u overflow se
resuelven como error HTTP 400 de tipo inválido.

La unicidad existente es por `negocio_id + store_id + commercial_number`.

## 4. Diseño de query

Se creó una única JPQL dinámica `TicketRepository.searchPaidHistory`:

```text
negocioId obligatorio
+ storeId obligatorio
+ status = PAID
+ paidAt >= from
+ paidAt < toExclusive
+ filtros opcionales
+ Pageable
```

El servicio impone orden `paidAt DESC, id DESC`. Spring Data ejecuta
paginación y count en BD. No existe paginación en memoria.

Se eligió JPQL dinámica en vez de una combinación exponencial de métodos
derivados. La query tiene parámetros opcionales y `@EntityGraph` para las
relaciones requeridas por el DTO.

## 5. Prevención de duplicados

La query no hace join con `payments`. Filtra el resumen persistido
`Ticket.paymentMethod`; por tanto:

- un MIXED con dos filas Payment aparece una vez;
- `totalElements` cuenta tickets;
- `totalPages` no se altera;
- no se necesita `DISTINCT`;
- el count generado conserva la misma cardinalidad.

La carga posterior de Payment continúa siendo batch por ids de la página.

## 6. Límite temporal

Se creó `ReportDateRangePolicy` y la propiedad:

```properties
app.reports.max-range-days=${REPORTS_MAX_RANGE_DAYS:366}
```

La medida es inclusiva:

```text
ChronoUnit.DAYS.between(from, to) + 1
```

- `2025-01-01` a `2026-01-01`: 366 días, permitido;
- hasta `2026-01-02`: 367 días, rechazado.

El error estable es `DATE_RANGE_TOO_LARGE` con HTTP 422. El rango no se
recorta ni convierte silenciosamente.

Se aplica mediante la validación central usada por:

- historial pagado;
- total;
- payment summary;
- user summary;
- product summary;
- daily summary;
- average-ticket;
- cash-closing.

## 7. Daily summary

Se conserva la agregación en memoria. Una agrupación SQL directa mediante
`DATE(paid_at)` interpretaría la representación de almacenamiento y podría
ignorar `Store.timezone` y DST bajo la estrategia B.

El máximo configurable de 366 fechas de negocio limita la dimensión
temporal y evita rangos ilimitados. El coste sigue dependiendo del número de
tickets pagados dentro del rango, riesgo documentado.

## 8. Índices

Revisión de esquema:

- `idx_tickets_tenant_store_status (negocio_id, store_id, status)`;
- `idx_tickets_tenant_store_paid (negocio_id, store_id, paid_at)`;
- `uk_tickets_tenant_store_commercial_number
  (negocio_id, store_id, commercial_number) WHERE commercial_number IS NOT NULL`;
- índices JPA/históricos para `paid_by`, `mesa_id` y `paid_at`;
- `idx_payments_tenant_store_paid` para agregados Payment existentes.

PostgreSQL puede combinar índices mediante bitmap scans, pero no se dispone
de `EXPLAIN ANALYZE` real. No se creó una migración especulativa ni un índice
redundante. La necesidad de un índice compuesto adicional debe decidirse
con planes PostgreSQL y cardinalidad real.

## 9. Endpoint modificado

Solo se amplió de forma compatible:

```text
GET /api/tickets/paid
```

Rutas, roles, DTO, códigos de éxito y parámetros existentes no cambiaron.
Los reportes restantes solo reciben el nuevo límite central.

## 10. Contrato HTTP

Se conserva `PageResponseDto<TicketResponseDto>` y:

- metadatos de página;
- número comercial numérico y formateado;
- mesa histórica persistida;
- usuario creador y pagador;
- método resumen CASH/CARD/MIXED;
- referencias de sesión de origen y pago;
- timestamps Store-aware en los cortes;
- endpoints de detalle para líneas, snapshots y descuentos.

No se exponen entidades JPA.

## 11. Validaciones y errores

| Caso | Código | HTTP |
|---|---|---:|
| paymentMethod enum inválido | `INVALID_ENUM_VALUE` | 400 |
| userId <= 0 | `INVALID_USER_ID` | 422 |
| mesaId <= 0 | `INVALID_MESA_ID` | 422 |
| commercialNumber <= 0 | `INVALID_COMMERCIAL_NUMBER` | 422 |
| número/texto/overflow no convertible | `INVALID_FIELD_TYPE` | 400 |
| page < 0 | `INVALID_PAGE` | 400 |
| size < 1 o > 100 | `INVALID_PAGE_SIZE` | 400 |
| rango incompleto/invertido | `INVALID_DATE_RANGE` | 422 |
| más de 366 días inclusivos | `DATE_RANGE_TOO_LARGE` | 422 |

`GlobalExceptionHandler` maneja ahora
`MethodArgumentTypeMismatchException`; parámetros de query inválidos no
caen al error genérico 500.

## 12. Tests añadidos/ampliados

`TicketStoreScopeRepositoryTest` pasa de 4 a 5 tests y cubre:

- sin filtros;
- CASH, CARD y MIXED;
- usuario y usuario de otra Store;
- mesa y mesa de otra Store;
- número comercial repetido entre Stores;
- combinación de cuatro filtros;
- MIXED con dos pagos sin duplicación;
- `totalElements`;
- orden estable por `paidAt DESC, id DESC`;
- límites exactos;
- tenant y Store;
- campos históricos principales del DTO y número formateado;
- page/size e ids no positivos.

La fixture incluye tenant 1 con Stores A/B, tenant 2, usuarios y mesas
distintos, CASH/CARD/MIXED, componentes múltiples y números comerciales
repetidos entre Stores.

`ReportDateRangePolicyTest` añade 4 tests:

- máximo inclusivo permitido;
- un día adicional rechazado;
- rango invertido/incompleto;
- configuración inválida.

Se conservan los 7 tests Madrid/Lima/DST de `BusinessTimeServiceTest`.

## 13. Smoke

Se creó:

`scripts/smoke-fase9-history-reports.ps1`

Utiliza una fixture controlada preexistente y recibe tokens e ids por
parámetros o entorno; no contiene secretos. Comprueba:

- resultado sin filtro;
- CASH/CARD/MIXED;
- filtros individuales y combinados;
- ausencia de duplicación;
- número repetido entre Stores;
- enum inválido;
- rango excesivo;
- caso Lima opcional.

No crea filas ni repara secuencias. Limpia referencias de tokens en
`finally`, no arranca backend y no genera logs. Su sintaxis PowerShell fue
validada.

No se ejecutó porque faltan API, tokens y fixture/credenciales.

## 14. Compile/test/verify

| Verificación | Resultado |
|---|---|
| suite específica | PASS; 16 tests |
| sintaxis del smoke | PASS |
| `.\mvnw.cmd clean compile` | BUILD SUCCESS; 201 fuentes |
| `.\mvnw.cmd test` | BUILD SUCCESS; 34 tests, 0 fallos/errores/skip |
| `.\mvnw.cmd clean verify` | BUILD SUCCESS; 34 tests; JAR generado |

Warning preexistente: `AuthService` usa/sobrescribe API deprecada.

## 15. PostgreSQL/Flyway

No ejecutado:

- `psql` no está disponible;
- faltan `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`;
- Docker CLI existe, pero el daemon no está activo;
- no se pudo ejecutar Flyway migrate/validate, Hibernate validate,
  Testcontainers ni `EXPLAIN ANALYZE`.

No se creó ni modificó migración. V9.1 continúa siendo la última.

## 16. Riesgos restantes

- validar JPQL, Flyway e índices con PostgreSQL real;
- ejecutar `EXPLAIN ANALYZE` con cardinalidad representativa;
- ejecutar el smoke de historial/reportes, timezone, F9 y regresión F8;
- daily summary todavía carga tickets del rango en memoria;
- el filtro de mesa refleja la relación final persistida, no un snapshot
  inexistente;
- H2 no sustituye PostgreSQL para planes ni tipos temporales;
- la política temporal continúa siendo estrategia B controlada.

## 17. Veredicto

**GO condicionado para filtros de historial y rendimiento controlado.**

La implementación combina todos los filtros sin romper tenant, Store,
timezone, límites exclusivos ni paginación. Compile/test/verify están
verdes. El cierre definitivo requiere PostgreSQL y smoke real.

No se migraron timestamps, no se implementó Fase 10 y no se hizo commit ni
push.
