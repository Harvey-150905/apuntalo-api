# Auditoría pre-Fase 6 — pagos y caja

Fecha: 2026-07-23  
Proyecto auditado: `C:\Users\AlfredoHarbeyPanduro\Desktop\apuntalo-api\api-apuntalo`  
Rama: `main`

## 1. Resumen ejecutivo

**Recomendación: NO-GO para “comenzar F6.1”.**

El motivo principal es que el estado real del repositorio no coincide con el contexto de partida: F6.1–F6.5 ya están implementadas en código y en migraciones (`V6_1` a `V6_5`), y el repositorio contiene además evolución posterior multi-Store (`V8.x`) y Fase 9 (`V9_1`). Implementar F6 de nuevo duplicaría entidades, tablas, contratos y reglas existentes.

La implementación encontrada incluye:

- entidad `Payment` con componentes `CASH` y `CARD`;
- resumen `MIXED` en `Ticket`;
- efectivo recibido y cambio calculado por backend;
- pago único del ticket con uno o dos componentes;
- catálogo de cajas;
- sesiones de caja;
- sesión de origen y sesión de cobro;
- movimientos `CASH_IN`/`CASH_OUT`;
- conciliación y cierre;
- idempotencia, auditoría y protecciones concurrentes;
- aislamiento por tenant y Store.

La compilación es satisfactoria, pero la evidencia automatizada es insuficiente: solo existen 18 tests y ninguno prueba pagos, cajas, sesiones, movimientos, conciliación, idempotencia operativa, aislamiento tenant/Store o carreras concurrentes de F6. Antes de modificar esta área debe reconstruirse la trazabilidad de lo ya implementado y añadirse una batería de caracterización/integración.

No se encontraron hallazgos `CRITICAL`. Los hallazgos `HIGH` son:

1. discrepancia entre el estado declarado (“Fases 1–5 cerradas”) y el repositorio real (F6 ya implementada y evolucionada hasta V9);
2. ausencia total de tests automatizados específicos de pagos/caja;
3. la configuración denominada “cash management/reconciliation” no hace opcional la caja: crear y pagar tickets exige siempre una sesión abierta. Esta decisión debe validarse con negocio y frontend.

## 2. Estado del repositorio

### Git

Comandos ejecutados antes de crear este informe:

- `git status --short`: sin salida; árbol limpio;
- `git diff --stat`: sin salida;
- `git diff --check`: sin errores;
- rama actual: `main`.

No había archivos modificados ni nuevos. Tras la auditoría, el único archivo intencionadamente creado es este informe.

No se ejecutaron `commit`, `push`, `checkout`, `switch`, `stash`, `reset`, restauraciones ni descartes.

### Migraciones presentes

En `src/main/resources/db/migration`:

- `V5_1__numbering_and_discounts.sql`
- `V5_2__audit_events.sql`
- `V6_1__payments.sql`
- `V6_2__cash_management_and_registers.sql`
- `V6_3__cash_sessions.sql`
- `V6_4__ticket_origin_and_payment_sessions.sql`
- `V6_5__cash_movements_and_session_closure.sql`
- `V8_1__stores_and_primary_store.sql`
- `V8_1_1__align_store_country_code_type.sql`
- `V8_2__user_store_access_and_default_store.sql`
- `V8_4__operational_store_scope.sql`
- `V8_5__store_scoped_catalog.sql`
- `V8_6__store_scoped_ticket_numbering.sql`
- `V8_7__store_scoped_audit_and_idempotency.sql`
- `V9_1__subcategory_active_flag.sql`

Flyway está configurado con baseline 4, validación al migrar, `out-of-order=false` y limpieza desactivada (`application.properties:18-28`). La última migración incluida en el artefacto es `V9.1`, no `V5.2`. No se conectó a una base de datos, por lo que no puede afirmarse cuál es la última versión efectivamente registrada en `flyway_schema_history` de un entorno concreto.

### Documentación, scripts y residuos

Documentación rastreada:

- `AUDITORIA_FASES_1_4_FINAL.md`
- `AUDITORIA_POST_FASES_1_4.md`
- documentación F9 bajo `docs/audits`, `docs/contracts`, `docs/implementation` y `docs/verification`.

Scripts rastreados:

- `scripts/verify-phases-2-4.ps1`
- `scripts/smoke-fase9-admin.ps1`

Se detectó `f9-backend.log` en la raíz. No figura entre los archivos rastreados y contiene la salida de un arranque anterior de Spring Boot. Es residuo local; no se eliminó por la restricción de la auditoría.

### Secretos

No se encontraron credenciales persistidas. `application.properties` usa variables de entorno para contraseña de BD, secreto JWT y Cloudinary (`application.properties:7-9,31,53-55`). Los passwords encontrados en `smoke-fase9-admin.ps1` son datos efímeros generados para usuarios QA, no secretos de infraestructura. Debe mantenerse `f9-backend.log` fuera de Git porque un log de runtime puede acabar capturando información sensible.

## 3. Resultado de Maven

| Comando | Resultado | Observaciones |
|---|---|---|
| `.\mvnw.cmd clean compile` | BUILD SUCCESS | 199 fuentes; 40.931 s |
| `.\mvnw.cmd test` | BUILD SUCCESS | 18 tests; 0 fallos, 0 errores, 0 omitidos |
| `.\mvnw.cmd clean verify` | BUILD SUCCESS | JAR generado; 18 tests; 0 fallos |

Tests existentes:

- `ApiApuntaloApplicationTests`: 1;
- `AdminAuthorizationRulesTest`: 17.

Warnings:

- `AuthService.java` usa o sobrescribe API deprecada; Maven recomienda recompilar con `-Xlint:deprecation`.
- `src/test/resources` no existe; Maven lo informa como directorio omitido.

No hay JaCoCo ni otro plugin de cobertura en `pom.xml`; no existe porcentaje de cobertura disponible. `verify` no ejecuta integración real con PostgreSQL/Flyway y no valida el esquema contra una BD.

## 4. Modelo de pago actual

### Estado exacto

Sí existe entidad `Payment`: `entity/Payment.java:10-65`. El pago no está solo embebido en `Ticket`, aunque `Ticket` mantiene un resumen compatible (`paidAt`, `paidBy`, `paymentMethod`) en `entity/Ticket.java:66-72,98-100`.

`Payment` guarda:

- `id`;
- `negocioId`;
- `store`;
- `ticket`;
- `cashSession`;
- `method`;
- `amount`;
- `cashReceived`;
- `changeGiven`;
- `paidBy`;
- `paidAt`;
- marcadores de histórico `legacyImported` y `sessionLegacy`;
- `createdAt`.

Referencias: `Payment.java:18-64`.

`PaymentMethod` contiene `CASH`, `CARD` y `MIXED` (`entity/enums/PaymentMethod.java:3-6`). Sin embargo, `MIXED` solo es resumen del ticket: la tabla `payments` limita componentes a `CASH` o `CARD` (`V6_1:26`), y el servicio rechaza un componente `MIXED` (`TicketService.java:1440-1444`).

### Cardinalidad y snapshot

- Un ticket se paga una sola vez porque el servicio bloquea el ticket y exige estado `OPEN` (`TicketService.java:320-360`).
- Un ticket tiene uno o dos registros `Payment`: como máximo uno `CASH` y uno `CARD`.
- La unicidad `(ticket_id, method)` está tanto en JPA (`Payment.java:11-12`) como en BD (`V6_1:21`).
- La suma de componentes debe ser exactamente el total congelado al pagar (`TicketService.java:1479-1485`).
- `Payment.amount`, `cashReceived`, `changeGiven`, `paidBy` y `paidAt` son snapshots históricos.
- `Ticket.total`, `paidBy`, `paidAt` y `paymentMethod` también quedan persistidos como resumen.

No existe colección JPA `Ticket -> payments`; la navegación se hace por `PaymentRepository.findByTicketIdAndNegocioIdOrderByIdAsc`. Sí existe la relación física `payments.ticket_id -> tickets`.

### Efectivo y cambio

- `CASH` exige `cashReceived`;
- `cashReceived < amount` produce `INSUFFICIENT_CASH`;
- el cliente no puede enviar `changeGiven`;
- el backend calcula `changeGiven = cashReceived - amount`;
- `CARD` rechaza campos de efectivo.

Referencias: `TicketService.java:1456-1478`; constraints equivalentes en `V6_1:26-37`.

El formato legado `paymentMethod` sigue aceptado y marcado `@Deprecated`. Para `CASH` legado, el backend presupone efectivo exacto (`cashReceived=total`, cambio cero), lo que preserva compatibilidad pero impide expresar el efectivo realmente entregado (`PayTicketRequestDto.java:16-20`; `TicketService.java:1407-1425`).

### Doble pago y concurrencia

El orden de locks del pago es sesión de cobro y después ticket. Ambos usan `PESSIMISTIC_WRITE` y scope tenant+Store (`TicketService.java:325-332`; `TicketRepository.java:179-188`; `CashSessionRepository.java:21-24`). La segunda petición espera y posteriormente encuentra el ticket ya pagado. `Ticket` tiene además `@Version` (`Ticket.java:33-36`).

La traducción general de bloqueo optimista/pesimista es HTTP 409, código `RESOURCE_CONCURRENTLY_MODIFIED` (`GlobalExceptionHandler.java:84-89`). Una violación de integridad no especializada se traduce en 409 `DATA_CONFLICT` (`GlobalExceptionHandler.java:91-95`).

## 5. Contrato HTTP actual

### Pago

`POST /api/tickets/{ticketId}/pay` (`TicketController.java:102-119`).

Headers:

- `Authorization: Bearer <JWT>` obligatorio por seguridad;
- `Content-Type: application/json`;
- `Idempotency-Key` declarado opcional en el controlador, pero en la configuración actual `app.idempotency.enforced=true`, por lo que en la práctica es obligatorio;
- el replay añade `Idempotency-Replayed: true`.

Request actual preferido:

```json
{
  "cashSessionId": 123,
  "payments": [
    {
      "method": "CASH",
      "amount": 20.00,
      "cashReceived": 25.00
    },
    {
      "method": "CARD",
      "amount": 10.00
    }
  ]
}
```

Formato legado aún compatible:

```json
{
  "cashSessionId": 123,
  "paymentMethod": "CARD"
}
```

`cashSessionId` es obligatorio (`PayTicketRequestDto.java:12-14`). La respuesta es `TicketDetailResponseDto`, con líneas, lista de `payments`, resumen `paymentMethod`, sesión de origen y sesión de cobro (`TicketService.java:1355-1378`).

Roles: `SUPER_ADMIN`, `ADMIN`, `CAMARERO` (`SecurityConfig.java:152-153`).

Tenant y Store: se obtienen del principal/contexto activo; el request normal no recibe `tenantId` ni `storeId`.

Códigos observables:

- 200: pago o replay correcto;
- 400: request/JSON/Idempotency-Key inválido;
- 401/403: autenticación/autorización;
- 404: ticket, usuario o sesión no visible en el tenant/Store;
- 409: ticket ya pagado/cancelado, sesión cerrada, registro inactivo o conflicto concurrente;
- 422: reglas monetarias/de componentes (`TICKET_EMPTY`, `PAYMENT_FORMAT_CONFLICT`, `PAYMENT_COMPONENTS_REQUIRED`, `MIXED_METHOD_NOT_ALLOWED_AS_COMPONENT`, `PAYMENT_METHOD_DUPLICATED`, `INVALID_PAYMENT_AMOUNT`, `CASH_FIELDS_NOT_ALLOWED_FOR_CARD`, `CLIENT_CHANGE_NOT_ALLOWED`, `CASH_RECEIVED_REQUIRED`, `INSUFFICIENT_CASH`, `PAYMENT_TOTAL_MISMATCH`, `INVALID_MONETARY_SCALE`).

### Compatibilidad frontend

No hay frontend en este repositorio, por lo que solo puede inferirse el contrato backend.

Cambios aditivos compatibles:

- añadir campos opcionales a response;
- mantener `paymentMethod` legado mientras se migra a `payments`;
- añadir nuevos endpoints o códigos sin cambiar los existentes.

Cambios rompientes:

- eliminar `paymentMethod`;
- retirar `cashSessionId`;
- cambiar nombres/tipos de `payments`, `method`, `amount`, `cashReceived`, `changeGiven`;
- aceptar `MIXED` como componente;
- cambiar status HTTP o forma de `ApiError`;
- hacer que `Idempotency-Key` deje de reproducir la respuesta exacta;
- alterar que el cambio lo calcule el servidor.

Observación: `PaymentComponentRequestDto.amount` está marcado `@NotNull`, aunque el servicio contiene una rama para inferir el total cuando hay un solo componente con amount nulo (`TicketService.java:1446-1449`). La validación HTTP hace esa rama inalcanzable; documentación y código deben alinearse en una futura corrección autorizada.

## 6. Elementos de caja existentes

| Elemento | Implementación | Estado |
|---|---|---|
| Caja | `CashRegister`, tabla `cash_registers`, repository/service/controllers/DTO | Funcional |
| Sesión | `CashSession`, tabla `cash_sessions`, repository/service/controllers/DTO | Funcional |
| Movimiento | `CashMovement`, tabla `cash_movements`, repository/service/controller/DTO | Funcional |
| Configuración | conciliación por Store; alias HTTP histórico `cash-management` | Funcional |
| Conciliación/cierre | campos persistidos en `cash_sessions` y servicio de cierre | Funcional |
| Relación ticket-sesión | `origin_cash_session_id` | Funcional |
| Relación pago-sesión | `payments.cash_session_id` | Funcional |

Endpoints:

- `POST /api/admin/cash-registers`
- `GET /api/admin/cash-registers`
- `GET /api/admin/cash-registers/{id}`
- `PATCH /api/admin/cash-registers/{id}`
- `PATCH /api/admin/cash-registers/{id}/status`
- `GET /api/cash-registers/active`
- `POST /api/cash-sessions/open`
- `GET /api/cash-sessions/my-open`
- `GET /api/cash-sessions/open`
- `POST /api/cash-sessions/{id}/movements`
- `GET /api/cash-sessions/{id}/movements?page=&size=`
- `POST /api/cash-sessions/{id}/close`
- `GET /api/admin/cash-sessions/open`
- `GET /api/admin/cash-sessions/{id}`
- `GET /api/admin/cash-sessions/{id}/summary`
- `GET /api/admin/cash-sessions/open/summaries`
- `GET /api/admin/cash-sessions/{id}/pending-tickets`
- `GET|PATCH /api/admin/cash-management/config`
- `GET|PATCH /api/admin/cash-reconciliation/config`

Los movimientos no tienen endpoints de edición/borrado: son append-only.

## 7. Estado del esquema y Flyway

Las migraciones F6 solicitadas ya existen con prácticamente los nombres orientativos:

- F6.1: `V6_1__payments.sql`;
- F6.2: `V6_2__cash_management_and_registers.sql`;
- F6.3: `V6_3__cash_sessions.sql`;
- F6.4: `V6_4__ticket_origin_and_payment_sessions.sql`;
- F6.5: `V6_5__cash_movements_and_session_closure.sql`.

No deben crearse otras migraciones `V6_*` ni modificarse las existentes. La evolución V8.4 añade `store_id`, constraints tenant+Store e índices a cajas, sesiones, tickets, pagos y movimientos (`V8_4:31-91`).

La división actual es coherente y no se proponen nombres futuros de implementación F6 porque hacerlo induciría una duplicación. Si tras las pruebas se detecta una corrección de esquema, debe llevar la siguiente versión libre posterior a `V9_1` (por ejemplo `V9_2__...`), determinada únicamente al aprobar el cambio.

Constraints relevantes:

- pago por método único y coherencia CASH/CARD;
- FK compuestas tenant y, desde V8.4, tenant+Store;
- una sesión `OPEN` por caja mediante índice parcial;
- una sesión `OPEN` por responsable mediante índice parcial;
- coherencia completa de campos de cierre;
- nombres de caja únicos normalizados por tenant+Store.

## 8. Tenant y seguridad

El flujo normal obtiene tenant del principal (`CurrentUser`) y Store de `ActiveStoreContext`; los DTO operativos no reciben `tenantId`. Las consultas auditadas de ticket/caja/sesión/pago/movimiento incluyen tenant y, en los caminos actuales, Store.

Las FKs compuestas agregadas por V8.4 evitan relaciones cruzadas entre:

- caja y Store;
- sesión y caja;
- ticket y mesa/sesión de origen;
- pago y ticket/sesión;
- movimiento y sesión.

Roles:

- catálogo/configuración y vistas administrativas: `ADMIN`/`SUPER_ADMIN`;
- apertura, operaciones de sesión y pago: también `CAMARERO`;
- un `CAMARERO` solo puede mover/cerrar/listar su propia sesión (`CashSessionOperationService.java:89`);
- `ADMIN` y `SUPER_ADMIN` pueden operar de forma supervisada, dentro del tenant/Store activo.

No se detectó un `findById` sin tenant en el flujo auditado de F6. Los `findById` simples observados para `Negocio` usan el propio tenant como id o pertenecen a la infraestructura interna de idempotencia. Sí existen `findById` simples fuera del alcance F6 en servicios generales (`UserService`, `NegocioService`); requieren una auditoría de autorización separada antes de reutilizarlos en código F6.

No se ha ejecutado una prueba dinámica con dos tenants/Stores, por lo que el aislamiento está respaldado por inspección estática y constraints, no por evidencia de integración.

## 9. Transacciones y concurrencia

### Protecciones existentes

- Todas las mutaciones principales son `@Transactional`.
- Ticket: `@Version` y `PESSIMISTIC_WRITE`.
- Sesión: `@Version` y `PESSIMISTIC_WRITE`.
- Pago vs cancelar/añadir línea/cambiar mesa: serializado por lock del ticket.
- Pago vs cierre: ambos toman primero lock de sesión; el pago luego toma ticket. El cierre bloquea la sesión antes de agregar totales.
- Apertura: orden documentado negocio → usuario → caja (`CashSessionService.java:66-92`).
- Una sesión abierta por caja/responsable: comprobación de servicio más índices únicos parciales (`V6_3:26-29`).
- Dos cierres: lock de sesión; la segunda recibe `CASH_SESSION_ALREADY_CLOSED`.
- Movimientos concurrentes: lock de sesión antes de calcular/persistir.
- Alta/renombrado de caja: constraint único y traducción de `DataIntegrityViolationException`.
- Locks optimistas/pesimistas: traducidos a 409 funcional.

### Riesgos o carencias

- No hay tests multihilo/transaccionales que demuestren los órdenes de lock y ausencia de deadlock.
- La traducción de conflicto de apertura inspecciona el texto del error para distinguir el índice del responsable (`CashSessionService.java:196-202`), lo que acopla la semántica al mensaje del driver PostgreSQL; debe probarse.
- El pago bloquea la sesión antes de buscar el ticket. Un `cashSessionId` válido y un `ticketId` inexistente produce trabajo de lock innecesario, pero conserva el orden global.
- El total del cierre se calcula mientras la sesión está bloqueada; todos los escritores F6 inspeccionados respetan ese lock. Cualquier nuevo escritor deberá conservarlo.

## 10. Idempotencia

Las operaciones F6 ya están registradas como scope `STORE` (`IdempotencyOperationScopes.java:17-23`).

| Operación actual | Recurso/hash | Respuesta reproducida | Riesgo concurrente |
|---|---|---|---|
| `TICKET_PAY` | ticketId + body completo | `TicketDetailResponseDto`, 200 | doble cobro; mitigado por idempotencia y lock |
| `CASH_SESSION_OPEN` | request (caja, fondo) | `CashSessionResponseDto`, 201 | doble apertura; índices parciales |
| `CASH_SESSION_CLOSE` | sessionId + body | `CashSessionCloseResponseDto`, 200 | doble cierre; lock |
| `CASH_MOVEMENT_CREATE` | sessionId + tipo/importe/motivo normalizado | `CashMovementResponseDto`, 201 | movimiento duplicado; lock/idempotencia |
| `CASH_REGISTER_CREATE` | nombre canónico | `CashRegisterResponseDto`, 201 | nombre duplicado; unique |
| `CASH_REGISTER_UPDATE` | id + nombre canónico | response de caja, 200 | renombrado concurrente; unique |
| `CASH_REGISTER_STATUS_UPDATE` | id + body | response de caja, 200 | cambio de estado concurrente; locks |
| `CASH_MANAGEMENT_CONFIG_UPDATE` | request | config, 200 | cambios simultáneos; transacción |

La identidad persistida desde V8.7 es tenant+Store+usuario+operación+key para scope Store (`V8_7:89-96`). El mismo key con body distinto debe producir conflicto, y un replay devuelve status/body guardados.

Riesgos pendientes de prueba:

- caída dejando estado `PROCESSING`;
- reapropiación tras timeout;
- dos nodos de aplicación;
- canonización de `BigDecimal` y strings;
- replay después de cambios de permisos/Store activa;
- colisión de una misma key entre formatos legado y compuesto.

## 11. Auditoría funcional

`AuditAction` ya incluye:

- `TICKET_PAID`;
- `CASH_REGISTER_CREATED`;
- `CASH_REGISTER_RENAMED`;
- `CASH_REGISTER_ACTIVATED`;
- `CASH_REGISTER_DEACTIVATED`;
- `CASH_SESSION_OPENED`;
- `CASH_RECONCILIATION_ENABLED`/`DISABLED`;
- `CASH_MOVEMENT_IN_CREATED`/`OUT_CREATED`;
- `CASH_SESSION_CLOSED`.

Referencias: `AuditAction.java:16-27`.

`TICKET_PAID` ya registra desglose de componentes, total, actor, sesión de origen, sesión/caja de cobro y responsable (`TicketService.java:1499-1523`). No hace falta una acción separada `TICKET_PAYMENT_RECORDED` salvo que negocio quiera auditar cada componente como evento independiente; actualmente un único evento representa la operación atómica.

`CASH_SESSION_CLOSED` incluye conciliación y diferencia. No existe evento separado `CASH_RECONCILIATION_COMPLETED`; el cierre ya lo cubre. Separarlo solo tendría sentido si la conciliación pudiera completarse después del cierre.

Carencia: no hay tests que prueben emisión, contenido, scope Store y ausencia de eventos de éxito tras rollback.

## 12. Política monetaria

Existe política central: `MoneyPolicy`.

- escala: 2;
- redondeo: `HALF_UP`;
- máximo: `99,999,999.99`;
- cero normalizado: `0.00`;
- columnas principales: `numeric(10,2)`;
- DTO: hasta 8 enteros y 2 decimales.

Referencias: `MoneyPolicy.java:5-13`; `Payment.java:41-48`; `CashSession.java:45-46,64-68`; `CashMovement.java:17`.

Aplicación actual:

- total de ticket: suma y normalización a 2 decimales;
- componente, efectivo recibido y cambio: política central;
- fondo inicial: política central;
- movimientos y efectivo contado: política central;
- esperado: fondo + CASH + CASH_IN − CASH_OUT;
- CARD no incrementa efectivo esperado;
- diferencia: contado − esperado, escala 2 `HALF_UP`.

Recomendación sin implementar: mantener una sola política `MoneyPolicy` con escala 2, `HALF_UP`, rango `numeric(10,2)` y validación estricta sin redondear inputs con más de dos decimales. Sustituir constantes monetarias duplicadas en servicios por la política central cuando exista autorización de refactor.

## 13. Decisiones pendientes de negocio

1. **¿Puede pagarse sin caja abierta?** Código actual: no. `cashSessionId` y sesión `OPEN` son obligatorios. **Validación de negocio pendiente** por contradicción con la compatibilidad prevista.
2. **¿La caja es obligatoria o configurable?** Código actual: obligatoria. La configuración solo activa/desactiva conciliación, no el uso de sesión. **PENDIENTE DE NEGOCIO** confirmar intención.
3. **¿CARD afecta efectivo esperado?** Código actual: no; solo aparece en ventas totales/cardSales. Diseño coherente.
4. **¿MIXED ahora o después?** Código actual: ya implementado como dos componentes CASH+CARD y resumen `MIXED`. No debe reimplementarse.
5. **¿Pago único o varios componentes?** Código actual: una operación de pago por ticket, con uno o dos componentes distintos.
6. **¿Cambio sobre componente CASH?** Sí.
7. **¿Sesión de origen distinta de sesión de cobro?** Sí; el modelo y los resúmenes lo contemplan explícitamente.
8. **¿Qué ocurre con OPEN al cerrar caja?** Se permite cerrar solo si el usuario reconoce explícitamente los pendientes; se persisten cantidad e importe. **PENDIENTE DE NEGOCIO** ratificar.
9. **¿Puede cerrarse con diferencia?** Sí; se persiste cualquier diferencia cuando la conciliación es requerida. **PENDIENTE DE NEGOCIO** definir umbrales/aprobación.
10. **¿Movimientos editables?** No; append-only.
11. **¿CAMARERO abre/cierra?** Sí, abre y puede cerrar su propia sesión; un admin puede cerrar de modo supervisado. **PENDIENTE DE NEGOCIO** ratificar.
12. **¿Quién administra catálogo?** `ADMIN` y `SUPER_ADMIN` dentro del contexto activo. **PENDIENTE DE NEGOCIO** confirmar si SUPER_ADMIN debe operar solo tras seleccionar tenant/Store.

## 14. Hallazgos por severidad

### CRITICAL

Ninguno confirmado.

### HIGH

1. **Estado de proyecto contradictorio.** F6 ya existe y el repositorio llega a F9. Bloquea iniciar una nueva implementación F6.
2. **Sin pruebas F6.** Los 18 tests no ejercitan pagos/caja. No hay evidencia automatizada de reglas, migraciones, seguridad o concurrencia.
3. **Caja siempre obligatoria.** El código no soporta negocios sin gestión de caja, aunque la configuración puede sugerirlo. Crear y pagar ticket requiere sesión abierta.

### MEDIUM

1. La última migración empaquetada es V9.1; el contexto y cualquier plan que trate V5.2 como última versión están obsoletos.
2. `amount` nulo para componente único parece soportado por servicio, pero Bean Validation lo rechaza antes.
3. `paymentMethod` legado sigue expuesto y permite CASH exacto implícito; aumenta la matriz contractual y puede ocultar qué formato usa el frontend.
4. Distinción del conflicto de apertura basada en texto de excepción PostgreSQL.
5. No hay validación de Flyway/JPA contra PostgreSQL en los tests ejecutados.
6. Cierre con pendientes y cierre con diferencia son reglas ya codificadas sin decisión de negocio aportada.
7. No se probó dinámicamente el aislamiento tenant/Store ni el comportamiento de SUPER_ADMIN.

### LOW

1. Warning de API deprecada en `AuthService`.
2. Constantes monetarias duplicadas (`MAX_OPENING_FLOAT`, `MAX_AMOUNT`) pese a existir `MoneyPolicy`.
3. Servicios/controladores F6 mezclan formato expandido y extremadamente compacto, reduciendo mantenibilidad; no se refactorizó.
4. Alias `cash-management` puede inducir a pensar que desactiva sesiones cuando solo controla conciliación.
5. `f9-backend.log` es basura local de runtime.
6. No existe reporte de cobertura.

### INFORMATIONAL

- Build completo verde.
- No se detectaron secretos persistidos.
- Árbol Git estaba limpio al iniciar.
- Constraints tenant+Store y locks presentan una base sólida por inspección estática.

## 15. Plan recomendado F6.1–F6.5

Como F6 ya está implementada, este plan es de **caracterización, validación y corrección**, no de creación desde cero.

### F6.1 — Pagos avanzados

- Objetivo: congelar y validar el contrato existente CASH/CARD/MIXED.
- Entidades: caracterizar `Payment` y resumen de `Ticket`; no crear entidades.
- DTO: decidir retirada futura del formato legado y resolver la contradicción de `amount`.
- Endpoint: conservar `POST /api/tickets/{id}/pay`.
- Migración: ninguna salvo defecto de esquema demostrado; nunca modificar V6.1.
- Idempotencia: tests de replay, body distinto, dos peticiones y dos nodos.
- Auditoría: verificar `TICKET_PAID` y rollback.
- Pruebas mínimas: CASH exacto/con cambio/insuficiente, CARD, MIXED, sum mismatch, doble pago, cancelar/pagar concurrente, tenant/Store cruzado.

### F6.2 — Catálogo de cajas

- Objetivo: validar ciclo de vida y unicidad por Store.
- Entidades: caracterizar `CashRegister`.
- DTO/endpoints: congelar alta/lista/detalle/rename/status/active.
- Migración: ninguna; V6.2+V8.4 ya existen.
- Idempotencia: alta, rename y status con replay/body diferente.
- Auditoría: todas las acciones de catálogo.
- Pruebas mínimas: nombres canónicos duplicados, desactivar con sesión abierta, tenant/Store, roles.

### F6.3 — Sesiones

- Objetivo: validar apertura, permisos y exclusividades.
- Entidades: caracterizar `CashSession` y `@Version`.
- DTO/endpoints: apertura, mi sesión y sesiones abiertas.
- Migración: ninguna; V6.3 ya existe.
- Idempotencia: apertura concurrente por caja/responsable.
- Auditoría: `CASH_SESSION_OPENED`.
- Pruebas mínimas: caja inactiva, usuario inactivo, dos aperturas simultáneas, una sesión por responsable, CAMARERO.

### F6.4 — Relación ticket/pago/caja

- Objetivo: decisión explícita sobre obligatoriedad y sesiones distintas.
- Entidades: relaciones existentes `originCashSession` y `payment.cashSession`.
- DTO/endpoints: documentar `originCashSessionId` y `cashSessionId`.
- Migración: ninguna; V6.4 ya existe.
- Idempotencia: incluir ids de sesión en hashes y probar Store activa.
- Auditoría: verificar metadatos de ambas sesiones.
- Pruebas mínimas: origen=cobro, origen≠cobro, sesión cerrada/inactiva, cierre concurrente, tenant/Store cruzado.

### F6.5 — Movimientos y conciliación

- Objetivo: ratificar reglas de cierre, diferencias y pendientes.
- Entidades: `CashMovement` append-only y campos de cierre de `CashSession`.
- DTO/endpoints: movimientos paginados, resumen, pendientes y cierre.
- Migración: ninguna; V6.5 ya existe.
- Idempotencia: movimiento/cierre concurrente y replay.
- Auditoría: movimientos y cierre/conciliación.
- Pruebas mínimas: CASH_IN/OUT, salida que deja esperado negativo, paginación, CARD excluida de efectivo, cierre con/sin conciliación, con pendientes, con diferencia, dos cierres y pago durante cierre.

Dependencias: F6.1 y F6.2 pueden caracterizarse en paralelo; F6.3 depende del catálogo; F6.4 depende de pagos+sesiones; F6.5 depende de F6.1–F6.4. Ninguna subfase debe cambiar producción hasta resolver las decisiones de la sección 13.

## 16. Archivos que sería necesario modificar

Para la siguiente iteración autorizada de **tests y documentación**, previsiblemente:

- nuevos tests bajo `src/test/java/.../service` y/o `controller`;
- recursos de integración bajo `src/test/resources`;
- `pom.xml` si se incorpora Testcontainers/JaCoCo;
- documentación de contrato F6 bajo `docs`.

Solo si negocio aprueba cambios de reglas/contrato:

- `PayTicketRequestDto.java`;
- `PaymentComponentRequestDto.java`;
- `TicketService.java`;
- servicios/controladores de caja y sesión afectados;
- `SecurityConfig.java` si cambian roles;
- una migración nueva posterior a V9.1 si cambia el esquema.

No deben editarse `V5_1`, `V5_2` ni ninguna migración `V6_*` aplicada.

## 17. Conclusión

**NO-GO para comenzar F6.1 como implementación nueva.**  
**GO condicionado para una fase previa de caracterización y pruebas de la F6 existente.**

La base está implementada y compila, pero antes de evolucionarla deben:

1. reconciliarse roadmap y repositorio;
2. confirmarse las reglas pendientes de negocio;
3. documentarse el contrato real usado por frontend;
4. ejecutarse Flyway/JPA contra PostgreSQL;
5. añadirse tests funcionales, tenant/Store, idempotencia y concurrencia.

Esta auditoría no implementó ninguna funcionalidad de Fase 6 ni modificó lógica, entidades, DTO, endpoints o migraciones.
