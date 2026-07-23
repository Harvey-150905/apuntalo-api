# Auditoría del estado real de la Fase 9

Fecha: 2026-07-23  
Proyecto: `C:\Users\AlfredoHarbeyPanduro\Desktop\apuntalo-api\api-apuntalo`  
Rama: `main`

## 1. Resumen ejecutivo

**Estado real: F9 administrativa implementada, con evidencia E2E documental previa, pero Fase 9 no puede declararse cerrada en el estado actual.**

**Veredicto: NO-GO.**

Los documentos del repositorio definen F9 como **Administración multi-tienda**, no como una fase nueva de historial/reportes. F9.1–F9.8 están implementadas y `docs/verification/F9_9_SMOKE_RESULTS.md` afirma una ejecución previa de 79 checks F9 y una regresión F8 de 48 casos. En esta auditoría no fue posible repetir ese smoke: no hay API en 8080, `psql` no está disponible, no existen variables de conexión y el script exige credenciales explícitas.

La revisión estática descubrió defectos no cubiertos por el smoke:

- los historiales paginados de tickets `PAID`, `OPEN` y `CANCELLED` consultan por tenant pero **no por Store activa**;
- varios reportes/conteos son tenant-wide aunque sus importes principales son Store-scoped;
- `cash-closing` y `average-ticket` mezclan numerador Store-scoped con denominador tenant-wide;
- la zona `Store.timezone` no se utiliza para fechas/reportes: se usa un `Clock` global y `LocalDateTime` sin zona;
- el historial no ofrece los filtros completos solicitados;
- algunos reportes cargan listas completas y agregan en memoria.

El primer punto constituye un **leak entre Stores del mismo tenant**. No es un leak entre tenants, pero viola el aislamiento operativo F8/F9. Los reportes monetarios y conteos pueden ser incorrectos en negocios multi-Store. Estos son bloqueantes explícitos para cerrar F9.

Por tanto, **el siguiente paso real no es Fase 10**. Primero debe ejecutarse un cierre mínimo de F9: corregir scopes, resolver la política temporal, completar filtros acordados y añadir tests multi-Store de historial/reportes.

## 2. Estado Git

Al inicio:

- rama: `main`;
- `git status --short`: `?? AUDITORIA_PRE_FASE_6.md`;
- `git diff --stat`: sin cambios rastreados;
- `git diff --check`: sin errores.

`AUDITORIA_PRE_FASE_6.md` es trabajo previo del usuario/turno anterior y se preservó. Este informe es el segundo archivo nuevo.

`f9-backend.log` estaba presente, ignorado y no rastreado. Contenía salida de un arranque previo, no se usó como fuente principal y se retiró al terminar esta auditoría conforme al encargo.

No se detectaron secretos persistidos. La configuración usa variables de entorno. El smoke recibe credenciales por parámetros y usa `PGPASSWORD` solo en el proceso.

No se hizo commit, push, checkout, switch, stash, reset ni discard.

## 3. Flyway

Migraciones incluidas:

| Migración | Finalidad |
|---|---|
| V5.1 | numeración y descuentos |
| V5.2 | auditoría funcional |
| V6.1–V6.5 | pagos y caja |
| V8.1 | Stores y Store Principal |
| V8.1.1 | alineación de tipo de country code |
| V8.2 | accesos User–Store y Store predeterminada |
| V8.4 | scope Store de entidades operativas |
| V8.5 | catálogo Store-scoped |
| V8.6 | numeración de tickets por Store |
| V8.7 | auditoría e idempotencia Store-scoped |
| V9.1 | bandera `subcategories.activo` e índice Store/estado/nombre |

### V8

- **V8.1:** crea `stores`, Principal única por tenant, timezone y metadatos.
- **V8.1.1:** corrige/alinea `country_code`.
- **V8.2:** crea `user_store_access`, `default_store_id`, backfills e invariantes tenant-safe.
- **V8.4:** añade `store_id` a mesas, cajas, sesiones, tickets, pagos y movimientos; crea FKs compuestas e índices.
- **V8.5:** hace Store-scoped catálogo/subcategorías/productos y snapshots relacionados.
- **V8.6:** mueve numeración comercial a secuencia por Store.
- **V8.7:** persiste scope tenant/Store en auditoría e idempotencia.

### V9.1

`V9_1__subcategory_active_flag.sql`:

```sql
ALTER TABLE subcategories ADD COLUMN activo boolean NOT NULL DEFAULT true;
CREATE INDEX idx_subcategories_store_active_name
    ON subcategories(negocio_id, store_id, activo, nombre, id);
```

Resuelve la ausencia de soft-delete/activación de subcategorías, alineándolas con productos. Dependen de ella:

- entidad `Subcategory`;
- DTO request/response de subcategoría;
- `SubcategoryService`;
- `GET/POST/PUT/DELETE /api/subcategories`;
- `PATCH /api/subcategories/{id}/status`;
- búsquedas activas/administración del catálogo.

No se aprecia otra migración F9 ausente para el contrato administrativo actual. Los defectos de scope encontrados pueden corregirse en repositorios/servicios usando columnas e índices ya existentes. Solo una decisión temporal que cambie tipos de columna (`timestamp with time zone`/`Instant`) podría requerir esquema nuevo; no debe decidirse silenciosamente.

La última migración **incluida** es V9.1. La documentación afirma que V9.1 se aplicó en una ejecución anterior. Esta auditoría no pudo consultar `flyway_schema_history`, por lo que no reconfirma la versión de una BD real.

## 4. Alcance documental

### Clasificación

| Documento | Estado |
|---|---|
| `docs/audits/F9_ADMINISTRACION_MULTITIENDA_AUDIT.md` | **OBSOLETO como estado**, útil como diseño inicial |
| `docs/contracts/F9_ADMINISTRACION_MULTITIENDA_CONTRACT.md` | **IMPLEMENTADO mayoritariamente**, contrato vigente |
| `docs/implementation/F9_ADMINISTRACION_MULTITIENDA_IMPLEMENTATION.md` | **IMPLEMENTADO**, su “F9.9 pendiente” quedó superado por verification |
| `docs/verification/F9_9_SMOKE_RESULTS.md` | **VERIFICADO documentalmente en ejecución previa**, no reproducido hoy |

El audit inicial declaraba ausentes provisionamiento, CRUD de Stores y gestión User–Store. Esos puntos ya existen; por eso esa conclusión es obsoleta.

El contrato fijó:

- rol global por tenant;
- acceso N:M User–Store;
- Principal inmutable/no desactivable;
- no borrado físico;
- guardas sobre caja abierta/default/último acceso;
- token con Store activa;
- endpoints de plataforma, Stores, usuarios y asignaciones;
- auditoría administrativa y orden de locks.

La implementación declara F9.1–F9.8 hechas. La verificación declara F9.9 GO con 79 PASS y regresión 48/48. Esa evidencia no cubre historial, reportes, timezone ni los endpoints paginados donde se halló el leak.

Conclusión documental: la F9 original del repositorio es administración multi-tienda. El alcance adicional de historial/fechas/reportes ya existía parcialmente antes de F9 y debe auditarse como requisito transversal de cierre, no atribuirse falsamente a V9.1.

## 5. Código implementado

### F9 administrativa

- F9.1: `AdminAuthorizationRules` y `AdminAuthorizationService`.
- F9.2: provisionamiento atómico de negocio + Principal + primer ADMIN.
- F9.3: CRUD, filtros, paginación y estado de Stores.
- F9.4: CRUD administrativo, filtros, paginación, estado y reset de usuarios.
- F9.5: listar/asignar/revocar accesos y cambiar default.
- F9.6: estado/auditoría de mesas, guard de ticket abierto, delete físico deshabilitado.
- F9.7: activación/auditoría de subcategorías y productos; V9.1.
- F9.8: guard de sesión de caja abierta al cambiar Store y guardas de desactivación.
- F9.9: script smoke y documento de resultados previos.

### Diferencia F9 frente a F8

F8 creó el modelo y aislamiento Store-scoped. F9 añadió la administración de ese modelo:

- provisionar tenants;
- administrar Stores;
- administrar usuarios y accesos;
- aplicar reglas de autoridad de ADMIN;
- activar/desactivar negocio/Store/usuario/mesa/catálogo;
- auditoría administrativa;
- guardas operativas que protegen sesión de caja, default e historia.

Historial y reportes de tickets no son introducidos por V9.1 ni por los controladores administrativos F9.

## 6. Endpoints

### Endpoints F9 administrativos

| Método | Ruta | Request/filtros | Response | Roles/scope |
|---|---|---|---|---|
| POST | `/api/platform/negocios/provision` | `TenantProvisionRequestDto` | `TenantProvisionResponseDto`, 201 | SUPER_ADMIN, tenant objetivo explícito |
| PATCH | `/api/platform/negocios/{id}/status` | `{active}` | `NegocioResponseDto` | SUPER_ADMIN |
| GET | `/api/admin/stores` | page,size,q,active,negocioId | Page Store | ADMIN propio; SUPER_ADMIN puede tenant objetivo |
| GET | `/api/admin/stores/{id}` | negocioId opcional | Store | igual |
| POST | `/api/admin/stores` | `StoreCreateRequestDto` | Store, 201 | ADMIN/SUPER_ADMIN con autoridad |
| PUT | `/api/admin/stores/{id}` | `StoreUpdateRequestDto` | Store | igual |
| PATCH | `/api/admin/stores/{id}/status` | `{active}` | Store | igual |
| GET | `/api/admin/users` | page,size,q,active,role,negocioId | Page User | ADMIN/SUPER_ADMIN |
| GET | `/api/admin/users/{id}` | negocioId opcional | detalle User | autoridad por subset de Stores |
| POST | `/api/admin/users` | `AdminUserCreateRequestDto` | detalle, 201 | ADMIN sin escalada; SUPER_ADMIN |
| PUT | `/api/admin/users/{id}` | `AdminUserUpdateRequestDto` | detalle | igual |
| PATCH | `/api/admin/users/{id}/status` | `{active}` | detalle | igual |
| POST | `/api/admin/users/{id}/reset-password` | password | 204 | igual |
| GET | `/api/admin/users/{id}/stores` | negocioId | lista accesos | ADMIN/SUPER_ADMIN |
| PUT | `/api/admin/users/{id}/stores` | set activo + default | lista accesos | igual |
| POST | `/api/admin/users/{id}/stores` | storeId/makeDefault | lista accesos | igual |
| DELETE | `/api/admin/users/{id}/stores/{storeId}` | replacement opcional | lista accesos | igual |
| PUT | `/api/admin/users/{id}/stores/default-store` | defaultStoreId | lista accesos | igual |
| PATCH | `/api/mesas/{id}/status` | `{active}` | Mesa | ADMIN/SUPER_ADMIN, Store activa |
| PATCH | `/api/subcategories/{id}/status` | `{active}` | Subcategory | ADMIN/SUPER_ADMIN, Store activa |
| PATCH | `/api/products/{id}/status` | `{active}` | Product | ADMIN/SUPER_ADMIN, Store activa |

Todos usan DTOs; no serializan entidades JPA. Store/User admin limitan `size` mediante `PaginationPolicy` (1–100).

### Historial/reportes existentes

- `GET /api/tickets/paid?from&to&page&size`
- `GET /api/tickets/open?page&size`
- `GET /api/tickets/cancelled?page&size`
- `GET /api/tickets/paid/total?from&to`
- `GET /api/tickets/paid/payment-summary?from&to`
- `GET /api/tickets/paid/user-summary?from&to`
- `GET /api/tickets/paid/product-summary?from&to`
- `GET /api/tickets/paid/daily-summary?from&to`
- `GET /api/tickets/paid/average-ticket?from&to`
- `GET /api/tickets/cash-closing?from&to`
- reportes de sesiones/caja mediante `/api/admin/cash-sessions/...`
- movimientos paginados `/api/cash-sessions/{id}/movements`.

## 7. Historial de tickets

### Implementado

- páginas para `PAID`, `OPEN`, `CANCELLED`;
- rango de fechas para `PAID`;
- `page >= 0`, `1 <= size <= 100`;
- orden solicitado por fecha descendente más `id` descendente;
- DTO con número comercial, totales, descuentos/snapshots de líneas, pagos CASH/CARD/MIXED, sesión de origen/cobro;
- carga batch de líneas y pagos, evitando N+1 obvio.

### Ausente/parcial

No hay un endpoint unificado de historial ni filtros por:

- método de pago;
- usuario;
- mesa;
- número comercial;
- caja/sesión;
- importe;
- búsqueda libre.

El estado se expresa por rutas separadas, no por filtro. Store no debe ser parámetro operativo: debe derivarse del JWT.

### Defecto bloqueante

Los métodos paginados usan:

- `findByNegocioIdAndStatusAndPaidAt...` (`TicketService:1170-1176`);
- `findByNegocioIdAndStatusOrderByCreatedAtDesc` (`1008-1013`);
- `findByNegocioIdAndStatusOrderByUpdatedAtDesc` (`1027-1032`).

Ninguno recibe `storeId`, pese a que existen variantes Store-aware no paginadas. Un usuario en Store A puede recibir tickets de Store B del mismo tenant. El sort `(fecha,id)` es estable, pero la query no está correctamente scoped.

## 8. Fechas y zona horaria

Configuración:

- `app.business-zone`, default `Europe/Madrid`;
- `TimeConfig` crea un único `Clock.system(ZoneId.of(zone))`;
- entidades guardan `LocalDateTime`;
- PostgreSQL usa `timestamp without time zone`;
- `Store` tiene `timezone`, validada con `ZoneId`, pero no alimenta el reloj ni los rangos de reportes.

Interpretación de rangos:

- `from` → `from.atStartOfDay()`;
- `to` → `to.plusDays(1).atStartOfDay()`;
- consulta `[from, to+1día)`.

El límite superior exclusivo es correcto y no pierde datos al final del día. Se valida `from <= to` y se evita overflow de `LocalDate.MAX`.

Problema **HIGH**: la fecha se interpreta como wall-clock global, no en la timezone de la Store. Una Store `America/Lima` (el propio smoke crea una) comparte `LocalDateTime` y cortes `Europe/Madrid`. La timezone persistida es decorativa para operaciones/reportes. En Stores de otras zonas los días comerciales y reportes pueden ser incorrectos. El uso de `LocalDateTime`/`timestamp without time zone` también pierde el offset original y hace ambigua la hora en transiciones DST.

No se debe corregir sin decidir una política:

1. almacenar `Instant`/UTC y convertir por `Store.timezone` (recomendado), o
2. mantener wall-clock por Store y construir rangos con su zona, documentando DST.

## 9. Reportes

| Reporte | Fuente | Estado de scope |
|---|---|---|
| total ventas | SUM Ticket | Store-scoped |
| método pago | SUM Payment CASH/CARD + total Ticket | Store-scoped |
| usuario | GROUP BY Ticket.paidBy | **tenant-wide** |
| producto | GROUP BY TicketLine | **tenant-wide** |
| diario | carga tickets y agrega en memoria | **tenant-wide** |
| media ticket | total Store / count tenant | **incorrecto** |
| cash-closing | importes Store / counts tenant | **inconsistente** |
| historial PAID/OPEN/CANCELLED | Page Ticket | **tenant-wide** |
| caja/sesión/movimientos | proyecciones/repos Store-scoped | correcto por inspección |

Detalles:

- `getUserSalesSummary` no pasa Store.
- `findProductSalesSummary` no filtra Store.
- `getDailySalesSummary` carga tickets de todo el tenant.
- `getAverageTicketSummary` usa SUM Store-scoped pero count tenant-wide.
- `getCashClosingSummary` usa totales/payment Store-scoped, pero `paidTickets` y `cancelledTickets` tenant-wide.
- los reportes de descuentos no tienen endpoint agregado específico; los descuentos sí aparecen en snapshots de líneas.

Todos los importes usan `BigDecimal`. Los rangos son inclusivo/exclusivo. Las listas de usuario/producto/diario no están paginadas.

## 10. Rendimiento

### Correcto

- `@EntityGraph` en tickets/pagos y acceso User–Store;
- batch de líneas/pagos para páginas;
- proyecciones SQL para caja/sesiones;
- índices V8.4 por tenant+Store+status/paid_at;
- límite de página 100 en endpoints que usan `PaginationPolicy`;
- orden estable en las páginas de tickets y auditoría.

### Hallazgos

1. `getDailySalesSummary` carga todos los tickets del rango y genera todos los días en memoria. Un rango grande consume memoria/CPU proporcional a tickets+días.
2. user/product/daily summaries devuelven listas sin paginación ni límite de rango.
3. catálogo (`mesas`, `products`, `subcategories`) devuelve listas completas. Puede ser razonable para TPV pequeño, pero no para catálogos grandes.
4. `CashRegisterService.findAll` y sesiones abiertas son listas sin límite; riesgo menor por cardinalidad esperada.
5. `Page` ejecuta count total; es correcto porque el contrato devuelve totalPages/totalElements. `Slice` rompería contrato.
6. Los índices Store-scoped existentes no benefician plenamente a queries defectuosas tenant-wide; corregir scope también alinea índices.

No se encontró serialización de entidades, `FetchType.EAGER` generalizado ni filtrado masivo en memoria salvo el resumen diario.

## 11. Tenant y Store

### Correcto

- tenant y Store activa provienen del principal;
- endpoints operativos no aceptan Store arbitraria;
- SUPER_ADMIN cross-tenant usa rutas administrativas y tenant objetivo explícito;
- auditoría e idempotencia tienen scope Store;
- numeración comercial es por Store;
- FKs compuestas protegen relaciones cruzadas;
- administración aplica autoridad por subset de Stores.

### Incorrecto

El aislamiento operativo falla en historial/reportes señalados. Es un IDOR/listado indirecto entre Stores autorizadas o no autorizadas del mismo negocio. La autorización HTTP no lo corrige porque la consulta ya omite Store.

Existen métodos repository heredados tenant-wide. Algunos son legítimos para guardas globales; otros fueron usados accidentalmente en flujos operativos. Deben clasificarse y evitarse mediante nombres/arquitectura que hagan difícil elegir la variante incorrecta.

## 12. Seguridad

| Capacidad | SUPER_ADMIN | ADMIN | CAMARERO |
|---|---:|---:|---:|
| plataforma/provisionar tenant | Sí | No | No |
| CRUD Stores | Sí, cross-tenant explícito | Sí, autoridad propia | No |
| CRUD usuarios/accesos | Sí | Sí, subset y sin escalada | No |
| mesas/catálogo listar | Sí | Sí | productos/mesas lectura |
| mesas/catálogo mutar/estado | Sí | Sí | No |
| historial pagado | Sí | Sí | Sí |
| historial open/cancelled | Sí | Sí | No |
| reportes generales | Sí | Sí | varios no sensibles |
| reporte por usuario | Sí | Sí | No |
| auditoría/caja admin | Sí | Sí | No |

`SecurityConfig` cubre rutas F9. No se detectó un endpoint administrativo principal completamente sin autenticación. El problema principal está en scope de datos, no en matchers.

## 13. Validaciones y errores

Implementado:

- Bean Validation en DTOs;
- JSON/enums/tipos inválidos → 400;
- auth → 401/403;
- no visible → 404;
- conflictos de estado/nombre/locks → 409;
- reglas de negocio → 422;
- paginación inválida → 400 (`INVALID_PAGE`, `INVALID_PAGE_SIZE`);
- rangos invertidos → 422 `INVALID_DATE_RANGE`;
- Store no autorizada/autoridad insuficiente con códigos funcionales;
- handler de `DataIntegrityViolationException` y locks evita 500 genérico en conflictos conocidos.

Riesgos:

- el smoke no recorre fechas inválidas, paginación límite ni los reportes;
- multipart de producto declara `throws Exception`; los errores Jackson parecen cubiertos por advice global, pero no hay test específico;
- no puede garantizarse ausencia total de HTTP 500 sin integración.

## 14. Tests reales

Resultados de esta auditoría:

| Comando | Resultado |
|---|---|
| `.\mvnw.cmd clean compile` | BUILD SUCCESS; 199 fuentes |
| `.\mvnw.cmd test` | BUILD SUCCESS; 18 tests, 0 fallos/errores/skip |
| `.\mvnw.cmd clean verify` | BUILD SUCCESS; JAR generado; 18 tests |

Warning: `AuthService` usa/sobrescribe API deprecada. No hay reporte de cobertura.

Los 18 tests cubren:

- 17 reglas puras de autorización administrativa;
- 1 prueba básica de aplicación/normalización.

No cubren:

- controllers/services F9;
- PostgreSQL/Flyway;
- tenant/Store;
- historial/reportes;
- timezone/DST;
- paginación;
- auditoría;
- idempotencia;
- concurrencia.

Smoke F9:

- documentación previa: 79 PASS / 0 FAIL / 0 SKIP y regresión F8 48/48;
- ejecución actual: **NO EJECUTADO** por falta de API, `psql`, variables y credenciales;
- no se dejó backend activo ni se creó log.

Riesgos del script:

- cubre administración A–K, no historial/reportes/timezone;
- permite algunos status alternativos (200/201), lo que puede ocultar deriva contractual;
- si falla antes de la sección de limpieza, el `catch` salta la limpieza de entidades y el `finally` solo vacía `PGPASSWORD`; puede dejar negocios/datos QA desactivables pendientes;
- no arranca ni detiene el backend, por lo que depende de un proceso externo;
- puede reparar secuencias si se pasa `-RepairQaSequences`, mutación explícita de QA;
- no contiene credenciales persistidas.

## 15. Hallazgos

### CRITICAL

1. **Historial paginado sin Store scope.** Expone tickets de otras Stores del mismo tenant en `/paid`, `/open`, `/cancelled`.

### HIGH

1. **Reportes multi-Store incorrectos/leaky:** user, product y daily son tenant-wide.
2. **Media y cash-closing mezclan scopes:** importes Store con conteos tenant.
3. **Timezone de Store ignorada:** cortes diarios globales y timestamps naïve.
4. **Evidencia GO insuficiente para estos ámbitos:** smoke no prueba historial/reportes/timezone y tests unitarios tampoco.

### MEDIUM

1. Historial sin filtros por método, usuario, mesa y número comercial.
2. Daily summary carga/agrega todo en memoria; reportes list devuelven resultados sin límite.
3. Script puede dejar datos QA si aborta antes de cleanup.
4. Documentación de implementación queda obsoleta frente al documento posterior de verificación.
5. No se reconfirmó Flyway contra PostgreSQL en esta ejecución.

### LOW

1. Catálogo y algunas listas administrativas/operativas no están paginados.
2. Warning de API deprecada en `AuthService`.
3. Algunos endpoints create antiguos retornan 200 donde el contrato moderno preferiría 201.
4. Repositorios conservan variantes tenant-wide fáciles de usar por error.

### INFORMATIONAL

- build/verify verde;
- F9 administrativa está ampliamente implementada;
- V9.1 es la última migración incluida;
- no se detectaron secretos persistidos.

## 16. Plan mínimo de cierre

### Ya cerrado

- modelo multi-Store F8;
- autorización administrativa base;
- provisionamiento;
- CRUD/paginación de Stores y usuarios;
- asignaciones/default;
- guardas de activación;
- soft-delete/estado de mesa, subcategoría y producto;
- auditoría administrativa;
- smoke administrativo previo A–K.

### Falta implementar

1. Scope `tenantId + storeId` en todas las páginas de historial.
2. Scope Store en user/product/daily summary y todos los conteos.
3. Política temporal efectiva por Store o decisión explícita de timezone global.
4. Filtros mínimos acordados para historial: método, usuario, mesa y número comercial, si forman parte del criterio de F9 aportado.

### Falta corregir

1. `average-ticket`: count Store-scoped.
2. `cash-closing`: paid/cancelled counts Store-scoped.
3. daily summary: query agregada por BD o rango máximo documentado.
4. cleanup del smoke dentro de `finally` o rutina segura que se ejecute tras fallos.
5. actualizar documentos GO cuando las correcciones estén verificadas.

### Falta probar para GO

- dos Stores con datos distintos: paid/open/cancelled nunca cruzan;
- todos los reportes comparados contra SQL Store-scoped;
- CASH/CARD/MIXED, descuentos y snapshots históricos;
- filtros combinados y orden estable;
- page negativo, size 0/101, fechas inválidas e invertidas;
- Stores en `Europe/Madrid` y `America/Lima`, límites de día y DST;
- roles SUPER_ADMIN/ADMIN/CAMARERO;
- smoke F9 A–K y regresión F8 después de corregir;
- Flyway `validate`/JPA `ddl-auto=validate` contra PostgreSQL.

## 17. GO o NO-GO

**NO-GO para declarar FASE 9 CERRADA.**

La administración multi-tienda está funcionalmente avanzada y tuvo una verificación previa, pero el aislamiento Store y la corrección de reportes/fechas no cumplen el alcance exigido. No debe iniciarse Fase 10 hasta resolver los hallazgos CRITICAL/HIGH y repetir la verificación.

No se implementó Fase 10 ni se realizaron cambios grandes de negocio. La única modificación funcional al workspace fue retirar el log local no rastreado solicitado; además se creó este informe.
