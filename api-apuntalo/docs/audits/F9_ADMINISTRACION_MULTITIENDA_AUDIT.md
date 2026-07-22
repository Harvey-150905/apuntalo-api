# Auditoría F9 — Administración multi-tienda

Fecha: 2026-07-22  
Alcance: auditoría estática del código y migraciones presentes en `api-apuntalo`.  
Restricción aplicada: no se modificó código productivo, entidades, endpoints ni migraciones.

## 1. Resumen ejecutivo

El modelo operativo multi-tienda está bien encaminado y es coherente con la alternativa 1 solicitada: un `User` pertenece directamente a un `Negocio`, mantiene un único `Role` global para ese negocio, se autoriza en una o varias Stores mediante `UserStoreAccess`, tiene una Store predeterminada persistida y trabaja en una única Store activa incluida en el JWT. La Store activa no se acepta en las operaciones TPV: se deriva del principal autenticado.

La protección del token es más fuerte que un JWT puramente autocontenido. En cada petición, `JwtAuthenticationFilter` delega en `TokenPrincipalResolver.resolve(...)`, que consulta PostgreSQL y verifica usuario activo, negocio activo, rol persistido, `tokenVersion` y acceso activo a una Store activa. Por ello, retirar el acceso a la Store activa, desactivar esa Store o desactivar el usuario bloquea el token en la siguiente petición. El token previo a un `switch-store` conserva su Store original y sigue siendo válido mientras ese acceso continúe vigente.

La Fase 8 cubre el aislamiento operativo: mesas, carta, cajas, sesiones, tickets, pagos, numeración, auditoría e idempotencia están Store-scoped en servicios/repositorios y protegidos por restricciones compuestas en SQL. No se encontraron colecciones JPA `EAGER` ni listas de Stores dentro del JWT.

La administración multi-tienda no está cerrada. Existe CRUD de usuarios previo, pero solo `SUPER_ADMIN` puede mutarlo, cada usuario nuevo queda forzosamente asignado a Principal y no existen endpoints para gestionar accesos o Store predeterminada. `StoreService` solo ofrece lecturas y normalización, y no tiene controller. El flujo `SUPER_ADMIN → negocio → Store Principal → primer ADMIN` no existe: crear un negocio no crea su Principal, por lo que después `UserService.save(...)` falla al buscarla.

## 2. Veredicto

**MANTENER MODELO ACTUAL**, formalizando sus invariantes antes de implementar Fase 9.

No se justifica introducir rol por Store: los casos A–G se representan con rol global más autorización N:M. La única capacidad que no ofrece es que la misma identidad sea `ADMIN` en una Store y `CAMARERO` en otra. No hay evidencia de que Apúntalo necesite esa complejidad; si producto la declara obligatoria, el veredicto debe cambiar a **AJUSTAR MODELO ANTES DE FASE 9**.

Hay **3 bloqueantes funcionales para iniciar el CRUD administrativo** (no bloqueantes de Fase 8):

1. No existe provisionamiento transaccional de negocio + Principal + primer administrador.
2. No existen comandos/endpoints de asignación, retirada y cambio de Store predeterminada.
3. No existe API CRUD de Stores, aunque hay entidad, repositorio y servicio parcial.

## 3. MODELO ACTUAL COMPROBADO

### 3.1 Diagrama de relaciones

```text
Negocio (negocios.id)
 ├── 1:N Store (stores.negocio_id, una Principal por negocio existente en F8)
 └── 1:N User  (users.negocio_id, Role global, default_store_id obligatorio)

User 1:N UserStoreAccess N:1 Store
         PK (user_id, store_id)
         tenant redundante y validado: negocio_id
         autorización: active
         sin role por Store

JWT → userId + tenantId + storeId activa + role global + tokenVersion
       ↓ cada petición
TokenPrincipalResolver → estado actual de User/Negocio/UserStoreAccess/Store en PostgreSQL
```

### 3.2 Tabla de entidades y responsabilidades

| Entidad | Tabla/campos relevantes | Responsabilidad y evidencia |
|---|---|---|
| `Negocio` | `negocios.id`, `activo`, `cash_reconciliation_enabled` | Tenant del usuario. `entity/Negocio.java:16-39`. |
| `User` | `users.negocio_id`, `role`, `default_store_id`, `activo`, `token_version` | Pertenece directamente a un tenant; no tiene `store_id`. `entity/User.java:45-71`. |
| `Store` | `stores.negocio_id`, `active`, `primary_store`, `code`, `timezone` | Unidad operativa del tenant. Unicidad por tenant de nombre/código y una única Principal por índice parcial. `entity/Store.java:8-84`; `V8_1...sql:61-105`. |
| `UserStoreAccess` | `user_store_access(user_id,store_id,negocio_id,active,assigned_at,assigned_by)` | Asociación N:M con estado; solo autoriza, no contiene rol. `entity/UserStoreAccess.java:21-63`. |
| `Role` | `users.role`: `SUPER_ADMIN`, `ADMIN`, `CAMARERO` | Rol único por usuario. `entity/Role.java:3-6`. |
| `AuditEvent` | `audit_events.negocio_id`, `store_id`, `scope_type`, `store_scope_legacy` | Eventos tenant- o Store-scoped. `service/AuditEventService.java:197-218`; `V8_7...sql:36-38,72-87`. |
| `IdempotencyRecord` | `idempotency_records.tenant_id`, `store_id`, usuario, operación, clave | Identidad idempotente independiente por Store o tenant. `service/IdempotencyService.java:102-135`; `V8_7...sql:89-96`. |

### 3.3 Cardinalidad e invariantes

- Un usuario pertenece **al Negocio directamente** y **a las Stores por autorización N:M**. No pertenece estructuralmente a una única Store.
- `User` sí tiene `negocio_id`; no tiene `store_id`; tiene `default_store_id NOT NULL` (`V8_2...sql:109,138-143`).
- `UserStoreAccessId` contiene `(user_id, store_id)` (`entity/UserStoreAccessId.java:13-24`). La tabla añade `negocio_id` para FKs tenant-safe.
- Un acceso se activa/desactiva mediante `active`; no hay fechas de revocación, motivo ni rol efectivo.
- Todo usuario persistido debe tener una Store predeterminada y la FK exige que exista la fila de acceso. La FK no puede exigir que `user_store_access.active=true`; esa regla deberá imponerse en el servicio administrativo.
- Un usuario con varias Stores tiene una fila activa por Store. Un usuario sin ninguna Store activa no es representable correctamente mediante las APIs actuales y no puede autenticarse; SQL impide eliminar la fila usada como default, pero no impide marcarla inactiva directamente.
- FKs `(user_id, negocio_id)` y `(store_id, negocio_id)` impiden relaciones entre tenants (`V8_2...sql:94-101`). `fk_users_default_store_tenant` impide default cross-tenant.
- Stores/usuarios inactivos permanecen para historia. El resolver rechaza usuario, negocio, acceso o Store inactivos (`security/TokenPrincipalResolver.java:35-55`).

## 4. Roles y autorización actual

### 4.1 Matriz

| Área | SUPER_ADMIN | ADMIN | CAMARERO | Evidencia |
|---|---:|---:|---:|---|
| Login, `/auth/me`, Stores autorizadas, switch | Sí | Sí | Sí | `SecurityConfig.java:61-63,165`; autenticado por defecto. |
| Leer usuarios del tenant | Sí (todos los tenants) | Sí (todo su tenant) | No | `SecurityConfig.java:78-85`; `UserService.java:80-105`. |
| Crear/editar/eliminar usuarios | Sí | No | No | `SecurityConfig.java:80-85`. |
| Leer negocio | Sí (todos) | Solo propio | No | `SecurityConfig.java:87-94`; `NegocioService.java:34-64`. |
| Mutar negocio | Sí | No | No | `SecurityConfig.java:89-94`. |
| Catálogo/mesas lectura | Sí | Sí | Sí en productos/mesas; no subcategorías | `SecurityConfig.java:96-102`. |
| Catálogo/mesas escritura | Sí | Sí | No | `SecurityConfig.java:96,99,102`. |
| Caja admin y auditoría | Sí | Sí | No | `SecurityConfig.java:65-76,161-163`. |
| Operaciones TPV/tickets | Sí | Sí | Sí según ruta | `SecurityConfig.java:104-159`. |

El rol se persiste una sola vez en `users.role`, se copia a la claim `role` y se compara con BD en cada petición (`JwtService.java:47-55`; `TokenPrincipalResolver.java:46-51`). No se observan `@PreAuthorize`; la autorización HTTP reside en `SecurityConfig` y el scope se aplica en servicios/repositorios.

`ADMIN` tiene autoridad global dentro de su negocio para endpoints administrativos permitidos, pero sus operaciones Store-aware usan exclusivamente la Store activa. No recibe acceso automático a todas las Stores: debe tener una fila activa en `user_store_access` para cambiar a ellas. Sí puede listar todos los usuarios del tenant, aun los asignados a Stores que él no tiene autorizadas.

`SUPER_ADMIN` también tiene un tenant y Store activos en el JWT, pero algunos servicios lo tratan como operador de plataforma: puede listar/localizar usuarios y negocios de otros tenants y crear/editar usuarios indicando `negocioId` (`UserService.java:80-105,168-188`). Las operaciones TPV siguen acotadas a su tenant/Store del token.

**¿Actualmente un mismo usuario puede ser ADMIN en una Store y CAMARERO en otra? No.** `User.role` es único y `UserStoreAccess` no contiene rol; `AuthenticatedUserPrincipal.role` tampoco varía al ejecutar `switch-store`.

## 5. Login, Store activa y vigencia de permisos

```text
POST /api/auth/login
 → AuthService.login(dto)
 → username trim + minúsculas
 → UserRepository.findByUsernameIgnoreCase (carga negocio)
 → password BCrypt + User.activo + Negocio.activo
 → User.defaultStore
 → UserStoreAccessRepository.findValidActiveStoreAccess
 → JwtService.generateToken(user, tenantId, defaultStoreId, tokenVersion)
 → JWT con una sola storeId

Petición autenticada
 → JwtAuthenticationFilter extrae y verifica firma/expiración/claims
 → TokenPrincipalResolver consulta BD
 → valida User, Negocio, role, tokenVersion, acceso y Store activos
 → AuthenticatedUserPrincipal.activeStoreId
 → CurrentUser / ActiveStoreContext
 → services y repositories tenant+Store
```

Evidencia: `AuthController.login(...)` (`controller/AuthController.java:28-31`), `AuthService.login(...)` (`service/AuthService.java:51-99`), `JwtService.generateToken(...)` (`service/JwtService.java:40-59`), `JwtAuthenticationFilter.doFilterInternal(...)` (`config/JwtAuthenticationFilter.java:52-115`) y `TokenPrincipalResolver.resolve(...)` (`security/TokenPrincipalResolver.java:31-67`).

Comportamiento exacto:

- Siempre se intenta elegir `users.default_store_id`; no se elige “la única” ni la primera de varias. Si no tiene default válido/activo, login devuelve `ACTIVE_STORE_NOT_AVAILABLE` (`AuthService.java:73-79`).
- Claim exacta de Store: `storeId`. No hay claim con todas las Stores.
- `POST /api/auth/switch-store` acepta solo `storeId` en el body, verifica que la Store pertenezca al tenant del principal, que exista acceso activo y que la Store esté activa, y emite otro JWT (`AuthService.java:102-117`; `UserStoreAccessService.java:65-80`).
- El token anterior no se revoca al cambiar: conserva su `storeId` y funciona si su acceso sigue activo. Esto permite sesiones paralelas por Store y coincide con el smoke F8.
- Retirar el acceso a una Store bloquea todos los tokens cuya `storeId` sea esa Store en la siguiente petición, aunque no se incremente `tokenVersion`, porque hay consulta a BD por petición.
- Desactivar usuario/negocio/Store bloquea inmediatamente. Cambiar rol, password o estado mediante `UserService.update(...)` incrementa `tokenVersion` (`UserService.java:132-161`).
- Riesgo residual de permisos obsoletos: un token antiguo de otra Store autorizada sigue válido deliberadamente; un cambio administrativo de acceso debe ser transaccional y preservar default. No hay caché ni ventana hasta expiración para accesos retirados.

## 6. Resolución de contexto y seguridad de recursos

`CurrentUser` lee solo `AuthenticatedUserPrincipal`, no headers ni parámetros (`security/CurrentUser.java:17-57`). `ActiveStoreContext.storeId()` obtiene la Store del principal y `requireStore()` vuelve a acotarla por tenant (`service/ActiveStoreContext.java:17-22`). Los endpoints TPV no solicitan `tenantId` ni `storeId`; la única entrada legítima de `storeId` es el comando explícito de cambio de contexto.

Comprobaciones representativas del flujo controller → service → repository:

- Mesas: `MesaController` → `MesaService.findAll/findById` → `findByNegocioIdAndStoreId...` (`MesaService.java:52-72`).
- Productos: `ProductController` → `ProductService` → `ProductRepository.findByIdAndNegocioIdAndStoreId` (`ProductService.java:90-169`; `ProductRepository.java:11-16`).
- Subcategorías: `SubcategoryController` → `SubcategoryService` → consultas tenant+Store (`SubcategoryService.java:49-70,82-101`).
- Cajas/sesiones: métodos activos usan tenant+Store, incluidos locks (`CashSessionService.java:56-103,118-156`; `CashRegisterService.java:158`).
- Tickets: los servicios usan el contexto de Store y las relaciones SQL ticket–mesa–línea–producto son Store-safe (`V8_4...sql:57-69`; `V8_5...sql:25-31`).
- Auditoría: listado limitado a tenant y a `storeId activa OR store_id IS NULL` (`AuditEventService.java:112-144`).

No se encontraron relaciones `FetchType.EAGER`; las asociaciones explícitas son `LAZY` y se usan `@EntityGraph` puntuales. Las respuestas observadas son DTOs, no entidades JPA. No hay indicio de serialización circular.

Existen repositorios con métodos tenant-wide heredados o antiguos (`CashSessionRepository.findByIdAndNegocioId`, `PaymentRepository.findByTicketIdAndNegocioId...`, `ProductRepository.findByIdAndNegocioId`, `MesaRepository.findByIdAndNegocioId`), pero los flujos operativos inspeccionados usan sus variantes Store-aware. Deben eliminarse o restringirse en un refactor posterior solo tras demostrar que no tienen consumidores; no constituyen por sí solos un bypass.

Política observada: recursos fuera del tenant/Store suelen producir `404` mediante búsquedas scoped; acceso explícito de switch sin asignación produce `403 STORE_ACCESS_DENIED`; Store inactiva al cambiar produce `409 STORE_INACTIVE`; token cuyo contexto dejó de ser válido produce `401 INVALID_TOKEN`. Es razonablemente consistente, aunque F9 debe documentarla como contrato.

## 7. Carga óptima del TPV

Flujo recomendado con los endpoints existentes:

1. `POST /api/auth/login`: guardar token, usuario, tenant y `activeStore` devueltos.
2. `GET /api/auth/me`: rehidratar contexto si la aplicación se recupera/refresca.
3. `GET /api/auth/stores`: cargar selector de Stores autorizadas activas.
4. Cargar en paralelo recursos de la Store activa: `GET /api/mesas/activas`, `GET /api/products/activos`, `GET /api/cash-registers/active`, `GET /api/cash-sessions/my-open` y, según pantalla, tickets de mesa/abiertos.
5. Al cambiar, `POST /api/auth/switch-store`; reemplazar el token y vaciar caches Store-scoped antes de recargar.

Inventario TPV real:

| Necesidad | Endpoint real | Scope |
|---|---|---|
| Contexto autenticado y Store activa | `GET /api/auth/me` | Usuario/tenant/Store activa |
| Stores autorizadas | `GET /api/auth/stores` | Accesos activos + Stores activas |
| Cambiar Store | `POST /api/auth/switch-store` | Store solicitada validada |
| Mesas | `GET /api/mesas`, `/api/mesas/activas`, `/api/mesas/{id}` | Store activa |
| Productos/carta | `GET /api/products`, `/api/products/activos`, `/api/products/{id}` | Store activa |
| Categorías | No existe CRUD/endpoint de `Category`; es enum. `GET /api/subcategories` expone agrupaciones persistidas | Store activa |
| Cajas | `GET /api/cash-registers/active`; admin `/api/admin/cash-registers` | Store activa |
| Sesiones | `GET /api/cash-sessions/my-open`, `/open`; admin `/api/admin/cash-sessions/...` | Store activa |
| Tickets | `/api/tickets/mesa/{mesaId}`, `/{ticketId}`, `/open`, reportes | Store activa |
| Configuración | `GET /api/admin/cash-management/config` y alias reconciliation | Store activa en implementación |

El frontend no debe enviar `storeId` con cada consulta; lo transporta el JWT. Carga una Store, no todas. El JWT mantiene tamaño constante (no contiene la colección de accesos), por lo que 5, 50 o más Stores son viables; el selector realiza una consulta ordenada no paginada, aceptable para decenas pero candidata a paginación/búsqueda si el producto prevé cientos o miles. `findAuthorizedActiveStores` usa `@EntityGraph("store")`, evitando N+1 (`UserStoreAccessRepository.java:64-74`). No se observan N+1 evidentes en esta ruta.

Al cambiar Store deben limpiarse mesas, carta/subcategorías, cajas, sesiones, tickets, reportes, auditoría y cualquier idempotency key pendiente. Pueden mantenerse identidad del usuario, tenant, rol global y preferencias verdaderamente tenant-wide. Una sesión de caja abierta en otra Store no aparece en `my-open` de la nueva Store, aunque impide abrir otra por la restricción tenant-wide; el frontend debería advertirlo antes del switch o disponer de contexto tenant-wide específico en F9.

## 8. Casos funcionales A–H

| Caso | Estado | Clases implicadas / riesgo / pendiente F9 |
|---|---|---|
| A. 5 Stores, usuarios exclusivos | **Parcialmente soportado** | El modelo N:M lo permite, pero no hay API para crear Stores/asignar accesos; el alta actual asigna siempre Principal. Implementar CRUD y asignación. |
| B. ADMIN en 5 Stores | **Parcialmente soportado** | Cinco accesos activos permiten switch; `Role.ADMIN` es global. Falta API de asignación y decidir si ADMIN puede autoadministrar accesos. |
| C. CAMARERO en Store 1 y 3 | **Parcialmente soportado** | Modelo y `switch-store` lo soportan para cualquier autenticado. Falta administración de filas. |
| D. Cambio durante sesión | **Soportado** | `AuthController.switchStore`, `AuthService.switchStore`, `UserStoreAccessService.resolveStoreForSwitch`; token nuevo, anterior intacto. Riesgo de caché frontend y sesión de caja oculta en otra Store. |
| E. Retirada con JWT activo | **Soportado técnicamente / administración ausente** | `TokenPrincipalResolver` revalida el acceso por petición. Falta endpoint transaccional, protección del default y regla sobre sesión de caja. |
| F. Desactivar Store | **Parcialmente soportado** | El resolver bloquea tokens y el switch; no existe comando CRUD. Debe impedirse/desaconsejarse si hay sesiones abiertas y preservarse historia. |
| G. Desactivar usuario | **Soportado** | `PUT /api/users/{id}` cambia `activo`, incrementa `tokenVersion`; el resolver también comprueba `activo`. Falta auditoría administrativa y reglas sobre caja abierta. |
| H. SUPER_ADMIN provisiona negocio | **No soportado** | `NegocioService.save` solo inserta `negocios`; `UserService.save` exige Principal. Falta caso de uso transaccional negocio + Principal + primer ADMIN y contrato de plataforma. |

## 9. Inventario administrativo existente

### 9.1 Usuarios

| Función | Estado | Evidencia/observación |
|---|---|---|
| Crear | **Existe parcialmente** | `POST /api/users`, solo SUPER_ADMIN; normaliza username, BCrypt, tenant validado y asigna Principal (`UserService.java:47-77`). No acepta Stores elegidas. |
| Listar/consultar | **Existe pero no es multi-tienda** | `GET /api/users[/{id}]`; ADMIN ve todo el tenant, respuesta no incluye accesos/default (`UserService.java:80-105`; `UserResponseDto.java:11-19`). |
| Editar/activar/desactivar | **Existe parcialmente** | `PUT /api/users/{id}`; cambia nombre, username, role, activo y opcionalmente password; invalida tokens en cambios sensibles (`UserService.java:118-166`). |
| Cambiar contraseña | **Existe parcialmente** | Dentro de PUT; no hay endpoint de autoservicio ni política más fuerte que mínimo 6 caracteres. |
| Cambiar rol | **Existe y está protegido** | Solo SUPER_ADMIN; rol global; incrementa tokenVersion. |
| Eliminar | **Existe, pero no es estrategia recomendada** | Solo si no existe ninguna fila de acceso, incluida inactiva (`UserService.java:108-116`). Preferir desactivación por historia. |
| Asignar/retirar Stores | **No existe** | Hay métodos de lectura/creación inicial internos, no controller administrativo. |
| Consultar Stores asignadas | **Solo para el propio usuario** | `GET /api/auth/stores`; no permite consultar las de otro usuario. |
| Definir Store predeterminada | **No existe** | Campo/constraints sí existen. |
| Primer admin del negocio | **No existe** | No hay orquestador de provisionamiento. |

Username es único global (`uk_users_username`) y se normaliza `trim().toLowerCase()` (`User.java:10-14`; `AuthService.java:151-152`). La contraseña se hashea con `PasswordEncoder` (`UserService.java:64,149-151`). La mutación no tiene auditoría funcional de usuario/Store. Los cambios sensibles invalidan JWT mediante `tokenVersion`; cambiar solo nombre/username no lo incrementa, aunque el principal reconstruye el username actual desde BD.

### 9.2 Stores

| Función | Estado |
|---|---|
| Crear | No existe endpoint/método de escritura. |
| Listar/consultar | Servicio interno `StoreService.findAll/findActive/findById/findPrimary`, sin controller. El usuario sí ve sus autorizadas mediante `/api/auth/stores`. |
| Editar nombre/código/configuración | Normalizadores existentes, pero no comando ni endpoint. |
| Activar/desactivar | No existe. |
| Eliminar | No existe. |
| Asignar/listar usuarios | Repositorio puede leer accesos por Store, sin servicio/controller administrativo. |

No debe ofrecerse borrado físico ordinario. Stores están referenciadas por usuarios/defaults, accesos, mesas, catálogo, cajas, sesiones, tickets, pagos, movimientos, secuencias, auditoría e idempotencia mediante FKs. La operación segura es desactivar, con política para sesiones abiertas y sin alterar históricos. Una eliminación excepcional solo sería viable como proceso de purga explícito y no como CRUD.

## 10. Migraciones, backfills y restricciones

| Migración | Efecto comprobado |
|---|---|
| `V8_1__stores_and_primary_store.sql` | Crea `stores`, una Principal determinista por negocio existente, unicidad tenant de nombre/código, una Principal por índice parcial, FKs de actores tenant-safe (`:61-120`). |
| `V8_1_1__align_store_country_code_type.sql` | Alinea `country_code` a `varchar(2)` conservando checks (`:39-80`). |
| `V8_2__user_store_access_and_default_store.sql` | Crea tabla N:M, asigna todos los usuarios legacy a Principal, rellena `default_store_id`, lo hace NOT NULL y añade FKs tenant/default-access (`:86-143`). |
| `V8_4__operational_store_scope.sql` | Añade y backfillea `store_id NOT NULL` a mesas, cajas, sesiones, tickets, pagos y movimientos; FKs Store-safe e índices (`:31-91`). |
| `V8_5__store_scoped_catalog.sql` | Añade Store a subcategorías/productos y tenant+Store a líneas; protege producto–subcategoría y línea–ticket/producto; unicidades Store (`:14-48`). |
| `V8_6__store_scoped_ticket_numbering.sql` | Secuencia y número comercial independientes por Store (`:20-38`). |
| `V8_7__store_scoped_audit_and_idempotency.sql` | Añade scope Store/tenant, conserva legacy ambiguo nullable marcado, FKs e índices únicos parciales por scope (`:36-97`). |

No quedan `store_id` nullable en tablas operativas o catálogo creadas por F8. Los únicos `store_id` nullable intencionales son auditoría/idempotencia tenant-wide o legacy (`scope_type`/`store_scope_legacy`).

Riesgos para F9:

- Crear un negocio después de V8.1 no dispara automáticamente la creación de Principal; el backfill solo actuó al aplicar Flyway.
- Al crear una Store hay que crear su fila de `ticket_number_sequences` en la misma transacción; F8.6 solo sembró Stores existentes.
- Cambiar tenant de User/Store no debe permitirse.
- `default_store_id` debe apuntar a acceso **activo** por regla de aplicación; la FK solo exige que la fila exista.
- Desactivar Store/acceso exige reasignar defaults afectados antes o dentro de la misma transacción.
- La Store Principal debe seguir siendo única y probablemente no desactivable mientras sea necesaria como fallback/provisionamiento.

## 11. Hallazgos por severidad

### BLOQUEANTE (3, para F9 administrativa)

1. **Provisionamiento de tenant incompleto.** `NegocioService.save(...)` no crea Store Principal y `UserService.save(...)` requiere una (`NegocioService.java:24-31`; `UserService.java:59`). Un negocio creado hoy no puede recibir su primer usuario mediante el flujo actual.
2. **No existe API de gestión User–Store/default.** El modelo la soporta pero no hay forma administrativa de satisfacer A–C ni de retirar accesos con invariantes.
3. **No existe API CRUD de Store.** `StoreService` es de lectura/validación y carece de controller; no puede gestionarse el ciclo de vida multi-tienda.

### ALTO (4)

1. **Cambios administrativos sin auditoría funcional.** Altas/ediciones/desactivaciones de usuario/negocio no llaman a `AuditEventService`; tampoco existen acciones enum para User/Store.
2. **Sin regla para acceso/default/sesión abierta.** F9 podría retirar el acceso activo o desactivar un usuario/Store responsable de una sesión abierta. Hoy existe unicidad de responsable tenant-wide (`CashSessionService.java:88-92`) pero no política administrativa.
3. **Switch con sesión abierta en otra Store.** Está permitido; `my-open` se filtra por la nueva Store y devuelve null, pero abrir otra caja falla tenant-wide. Requiere UX/contrato o endpoint de sesión abierta global, no necesariamente prohibir el switch.
4. **CRUD de negocio físico e incompleto.** `DELETE /api/negocios/{id}` intenta borrar; las FKs normalmente lo impedirán y no existe desactivación/provisionamiento coordinado (`NegocioService.java:66-70`).

### MEDIO (5)

1. ADMIN puede leer todos los usuarios del tenant, no solo los de Stores asignadas; decidir alcance funcional antes de F9.
2. `GET /api/auth/stores` no está paginado; correcto para decenas, revisar si se prevén cientos/miles.
3. DTO de usuario no muestra `defaultStoreId` ni accesos; insuficiente para formulario administrativo.
4. Política de contraseñas de administración es mínimo 6 caracteres y no hay endpoint especializado de reset.
5. Persisten métodos repository tenant-wide junto a variantes Store-aware; aumentan riesgo de uso accidental futuro aunque los flujos auditados usan scope correcto.

### BAJO (3)

1. `StoreService` tiene normalización útil pero no una API consumidora; puede reutilizarse en F9.
2. `switch-store` diferencia Store inexistente (`404`), no asignada (`403`) e inactiva (`409`); documentar si esa revelación dentro del tenant es deseada.
3. `NegocioService` usa `SecurityUtils` mientras los módulos nuevos usan `CurrentUser`; inconsistencia interna sin bypass demostrado.

### INFORMATIVO (5)

1. No hay `@PreAuthorize`; la matriz está centralizada en `SecurityConfig`.
2. No se encontraron colecciones EAGER ni Stores embebidas en JWT.
3. Las respuestas usan DTOs, no entidades JPA directas.
4. FKs compuestas ofrecen defensa en profundidad contra cruces tenant/Store.
5. La revalidación por petición prioriza revocación inmediata a costa de consultas adicionales a PostgreSQL.

## 12. Respuestas a las 20 decisiones

1. **¿Pertenencia?** Al Negocio directamente y a Stores mediante autorizaciones N:M.
2. **Cardinalidad?** User N:M Store por `user_store_access`.
3. **Store usada?** Claim `storeId`, validada y convertida en `AuthenticatedUserPrincipal.activeStoreId`.
4. **Stores autorizadas?** Filas activas de `user_store_access` unidas a Stores activas.
5. **Varias Stores?** Login en default; lista autorizadas; switch emite nuevo token.
6. **Rol?** Global por usuario.
7. **ADMIN opera todas automáticamente?** No; solo aquellas con acceso activo, una cada vez.
8. **ADMIN limitado?** Sí, mediante filas de acceso.
9. **CAMARERO puede cambiar?** Sí, a cualquier Store autorizada activa.
10. **Qué ocurre?** Recibe JWT nuevo; el anterior conserva Store; frontend debe vaciar datos Store-scoped.
11. **Frontend envía Store?** Solo al endpoint de switch; no en operaciones TPV.
12. **Crear usuario?** F9 debe aceptar datos personales, rol global, tenant resuelto/autorizado, Stores iniciales y default, y persistir todo transaccionalmente con password hash y auditoría.
13. **Formulario?** nombre, username, password inicial/reset separado, rol, activo, lista de Stores autorizables, selección múltiple y una default entre las seleccionadas.
14. **Asignación?** Upsert/reactivación de `UserStoreAccess` tenant-safe; default obligatorio dentro del conjunto activo.
15. **Retirar acceso activo?** Bloquear si es el último; si es default, exigir nueva default; resolver antes cualquier sesión de caja según política.
16. **JWT emitidos?** El token de esa Store ya será rechazado por revalidación. Opcionalmente incrementar `tokenVersion` para revocar todos los tokens si producto desea cierre global.
17. **Sesión abierta?** Recomendación: impedir retirada/desactivación mientras sea responsable, o exigir cierre/transferencia explícita auditada. Hoy no existe transferencia.
18. **Escala?** Sí para múltiples Stores; JWT O(1), índices tenant+Store y consultas scoped. El listado no paginado es el único límite visible para volúmenes muy altos.
19. **Bien resuelto?** Modelo N:M, default, active Store, switch, revocación por petición, FKs compuestas, aislamiento operativo, idempotencia y auditoría Store-aware.
20. **Falta?** Contrato de autoridad, provisionamiento, CRUD Store, gestión de accesos/default, auditoría administrativa, reglas con referencias/sesiones y smoke F9.

## 13. MODELO RECOMENDADO

### Alternativa 1 — rol global + autorización User–Store

- **Complejidad:** baja; coincide con entidades, JWT, authorities y migraciones actuales.
- **Seguridad:** clara: rol determina capacidad y acceso determina ámbito. Debe mantenerse la intersección `rol permitido AND Store autorizada`.
- **Flexibilidad:** cubre usuarios mono/multi-Store y ADMIN limitado o multi-Store; no cubre rol diferente por Store.
- **JWT/Spring Security:** sin cambios; `role` global y `storeId` activa ya funcionan.
- **Administración/BD:** CRUD sobre `User`, `Store` y `UserStoreAccess`; invariantes sobre default y sesiones.
- **Compatibilidad:** máxima con F8 y su smoke.
- **Necesidad para Apúntalo:** suficiente según casos A–H descritos.

### Alternativa 2 — rol efectivo en User–Store

- **Complejidad:** alta; nuevo campo/constraint y definición de rol fuera de Store para login/listados/plataforma.
- **Seguridad:** permite mínimo privilegio por Store, pero añade riesgos de desincronización entre claim y asignación y más casos de revocación.
- **Flexibilidad:** soporta ADMIN en una Store y CAMARERO en otra.
- **JWT/Spring Security:** `switch-store` debe recalcular rol; filtro debe comparar rol efectivo; token previo conserva el rol de su Store. Todas las authorities pasan a ser contextuales.
- **Administración/BD:** formularios y validaciones por cada asignación; migración/backfill del rol actual a todos los accesos.
- **Compatibilidad:** impacto transversal en F8, seguridad y smoke; requiere revalidación completa.
- **Necesidad:** no demostrada por los casos requeridos.

### Recomendación concreta

**MANTENER MODELO ACTUAL.** Antes de F9, aprobar explícitamente que el rol es global dentro del negocio. Si producto exige roles distintos por Store, esa decisión es arquitectónica y debe tomarse antes de construir los CRUD; en ese único caso se debe ajustar el modelo primero.

## 14. Decisiones funcionales pendientes

1. ¿ADMIN puede administrar usuarios/accesos o continúa siendo exclusivo de SUPER_ADMIN?
2. Si ADMIN administra, ¿solo usuarios cuya intersección de Stores esté contenida en sus propias Stores?
3. ¿SUPER_ADMIN es operador de plataforma cross-tenant o debe usar contexto explícito de tenant para toda acción?
4. ¿La Store Principal puede renombrarse, desactivarse o cambiarse? Recomendación inicial: renombrable; no eliminable; desactivación muy restringida.
5. ¿Retirar un acceso revoca solo tokens de esa Store (comportamiento actual) o todos mediante `tokenVersion`?
6. ¿Debe prohibirse cambiar de Store cuando el usuario es responsable de caja abierta en otra, o solo advertirse?
7. ¿Se permite transferir una sesión de caja? Actualmente no.
8. ¿Usuarios inactivos deben conservar accesos inactivos o activos para eventual reactivación?
9. ¿Categorías seguirán como enum o pasarán a entidad administrable? Hoy solo subcategorías son persistidas.
10. ¿El alta de negocio y primer admin será un endpoint atómico o un workflow por pasos con estado `PENDING_SETUP`?
11. ¿Se acepta username global o se desea unicidad por tenant? Cambiarlo afectaría login y migración; mantener global evita ambigüedad.

## 15. Propuesta de subfases F9

### F9.1 Contrato administrativo e invariantes

- Objetivo: cerrar decisiones anteriores, matriz de autoridad y códigos 404/403/409.
- Entidades: User, Store, UserStoreAccess, CashSession.
- Endpoints/migraciones: ninguno; especificación OpenAPI/DTOs.
- Autorización: definir plataforma vs tenant y alcance ADMIN.
- Auditoría: catálogo de nuevas acciones.
- Pruebas: matriz de permisos y casos A–H como contrato.
- Dependencias: ninguna; bloquea todo lo demás.

### F9.2 Provisionamiento de negocio

- Objetivo: crear atómicamente Negocio, Store Principal, secuencia y primer ADMIN/acceso/default.
- Endpoints: comando de provisionamiento SUPER_ADMIN; evitar reutilizar CRUDs de forma no atómica.
- Migraciones: probablemente ninguna si las tablas actuales bastan; quizá constraints/defaults solo si el contrato lo exige.
- Auditoría: negocio, Store, usuario y asignación creados con correlación.
- Pruebas: rollback completo, duplicados, tenant-safe, login inmediato.
- Dependencia: F9.1.

### F9.3 CRUD de Stores

- Objetivo: crear, consultar, editar y activar/desactivar sin borrado ordinario.
- Entidades: Store, TicketNumberSequence; referencias operativas para guardas.
- Endpoints: `/api/admin/stores`, `/{id}`, `/{id}/status`.
- Migraciones: no necesarias inicialmente; evaluar `version` optimista y metadatos de desactivación.
- Autorización: SUPER_ADMIN y/o ADMIN según F9.1, siempre tenant-scoped.
- Auditoría/pruebas: cambios before/after; unicidad normalizada, Principal, sesión abierta, cross-tenant.
- Dependencia: F9.2 para factorizar creación.

### F9.4 CRUD de usuarios

- Objetivo: sustituir/completar el CRUD actual con contratos administrativos seguros.
- Endpoints: listado paginado/filtrado, detalle, alta, edición, estado, reset password; no DELETE ordinario.
- Migraciones: probablemente ninguna; revisar política de credenciales y versionado.
- Autorización: según matriz; prohibir escalada de rol.
- Auditoría/pruebas: username normalizado/global, BCrypt, tokenVersion, cross-tenant.
- Dependencias: F9.1 y F9.3.

### F9.5 Asignación User–Store y default

- Objetivo: listar/asignar/reactivar/retirar accesos y cambiar default de forma transaccional.
- Endpoints: `/api/admin/users/{id}/stores`, asignación por lote, retirada y `/default-store`.
- Migraciones: opcional añadir `revoked_at/by/reason` o `@Version`; no es imprescindible para funcionalidad básica.
- Reglas: misma tenant, Store activa para asignar/default, al menos un acceso activo, default activo, política de caja abierta, locks de User/accesos.
- Auditoría/pruebas: concurrencia, última Store, default retirada, JWT activo, Stores cross-tenant.
- Dependencias: F9.3–F9.4.

### F9.6 CRUD de mesas

- Objetivo: consolidar el CRUD existente como administración Store-activa.
- Endpoints: los actuales `/api/mesas`; decidir status/desactivación y paginación.
- Migraciones: normalmente ninguna.
- Autorización/auditoría: ADMIN/SUPER_ADMIN; añadir eventos administrativos.
- Pruebas: unicidad por Store, referencia por tickets, IDOR.
- Dependencia: F9.3.

### F9.7 Catálogo: categorías, subcategorías y productos

- Objetivo: definir primero si `Category` sigue enum; completar CRUD Store-aware.
- Endpoints: actuales de subcategorías/productos y, solo si producto lo decide, categorías.
- Migraciones: solo si Category se convierte en entidad; evitar hacerlo por inercia.
- Autorización/auditoría: ADMIN/SUPER_ADMIN, Store activa.
- Pruebas: unicidad, referencias de líneas históricas, imágenes y cross-Store.
- Dependencias: F9.1 y F9.3.

### F9.8 Reglas de desactivación, autorización y auditoría

- Objetivo: cerrar guardas transversales y auditoría de todos los comandos.
- Entidades: Store, User, Access, Mesa, catálogo, CashSession.
- Migraciones: acciones/tipos son enums Java; SQL solo si se añaden metadatos/constraints.
- Pruebas: sesiones abiertas, referencias históricas, revocación inmediata, concurrencia y rollback.
- Dependencias: F9.3–F9.7.

### F9.9 Smoke administrativo multi-tienda

- Objetivo: validar A–H y no regresión de las 48 comprobaciones F8.
- Pruebas mínimas: dos tenants; cinco Stores; ADMIN limitado/global; CAMARERO multi-Store; retirada/desactivación con tokens antiguos; IDOR; cajas; idempotencia; auditoría; concurrencia.
- Dependencias: todas.

## 16. Archivos que probablemente deberán modificarse posteriormente

Lista prospectiva; ninguno fue modificado en esta auditoría:

```text
src/main/java/com/harbeyescala/api_apuntalo/config/SecurityConfig.java
src/main/java/com/harbeyescala/api_apuntalo/controller/NegocioController.java
src/main/java/com/harbeyescala/api_apuntalo/controller/UserController.java
src/main/java/com/harbeyescala/api_apuntalo/controller/StoreAdminController.java                 [nuevo]
src/main/java/com/harbeyescala/api_apuntalo/controller/UserStoreAccessAdminController.java       [nuevo]
src/main/java/com/harbeyescala/api_apuntalo/dto/UserRequestDto.java
src/main/java/com/harbeyescala/api_apuntalo/dto/UserUpdateDto.java
src/main/java/com/harbeyescala/api_apuntalo/dto/UserResponseDto.java
src/main/java/com/harbeyescala/api_apuntalo/dto/StoreCreateRequestDto.java                        [nuevo]
src/main/java/com/harbeyescala/api_apuntalo/dto/StoreUpdateRequestDto.java                        [nuevo]
src/main/java/com/harbeyescala/api_apuntalo/dto/UserStoreAssignmentRequestDto.java                [nuevo]
src/main/java/com/harbeyescala/api_apuntalo/entity/enums/AuditAction.java
src/main/java/com/harbeyescala/api_apuntalo/entity/enums/AuditEntityType.java
src/main/java/com/harbeyescala/api_apuntalo/repository/StoreRepository.java
src/main/java/com/harbeyescala/api_apuntalo/repository/UserRepository.java
src/main/java/com/harbeyescala/api_apuntalo/repository/UserStoreAccessRepository.java
src/main/java/com/harbeyescala/api_apuntalo/repository/CashSessionRepository.java
src/main/java/com/harbeyescala/api_apuntalo/repository/TicketNumberSequenceRepository.java
src/main/java/com/harbeyescala/api_apuntalo/service/NegocioService.java
src/main/java/com/harbeyescala/api_apuntalo/service/StoreService.java
src/main/java/com/harbeyescala/api_apuntalo/service/UserService.java
src/main/java/com/harbeyescala/api_apuntalo/service/UserStoreAccessService.java
src/main/java/com/harbeyescala/api_apuntalo/service/TenantProvisioningService.java                [nuevo]
src/main/java/com/harbeyescala/api_apuntalo/service/AuditEventService.java
src/main/resources/db/migration/V9_x__...sql                                                [solo si F9.1 lo exige]
src/test/java/com/harbeyescala/api_apuntalo/...                                             [nuevas pruebas]
```

No se recomienda modificar `JwtService`, `JwtAuthenticationFilter`, `AuthenticatedUserPrincipal` ni las migraciones V8 si se mantiene la alternativa 1.

## 17. Criterios de aceptación para comenzar implementación

- Rol global aprobado explícitamente o decisión de rol por Store tomada antes del CRUD.
- Matriz SUPER_ADMIN/ADMIN/CAMARERO aprobada, incluido alcance cross-tenant de SUPER_ADMIN.
- Contrato de provisionamiento de tenant y primer administrador definido.
- Política aprobada para Principal, eliminación/desactivación de Store y referencias históricas.
- Reglas aprobadas para última Store, default y sesión de caja abierta.
- Contrato de revocación JWT decidido: Store afectada vs todas las sesiones.
- DTOs/endpoints y semántica 404/403/409 documentados.
- Acciones de auditoría administrativa enumeradas sin datos sensibles.
- Estrategia de locks/concurrencia definida para asignaciones/default y desactivaciones.
- Smoke F9 incluye y conserva íntegro el smoke F8 de 48 comprobaciones.

## 18. Preguntas para el propietario del producto

1. ¿Una persona necesita realmente roles diferentes por Store?
2. ¿ADMIN puede crear/editar usuarios y asignaciones, o solo SUPER_ADMIN?
3. ¿Un ADMIN limitado a Stores A/B puede siquiera ver usuarios de C/D?
4. ¿Debe permitirse cambiar de Store con una caja abierta en otra?
5. ¿Quién puede cerrar o transferir una caja si se desactiva a su responsable?
6. ¿Desactivar una Store debe forzar cierre previo de cajas/tickets o permitir cierre controlado posterior?
7. ¿La Principal es permanente y obligatoriamente activa?
8. ¿Al retirar un acceso se cierran todas las sesiones del usuario o solo deja de funcionar esa Store?
9. ¿El alta de tenant debe ser atómica y devolver credenciales iniciales, o enviar invitación/reset?
10. ¿Categorías son una taxonomía fija del producto o deben administrarse por Store?
11. ¿Cuál es el máximo esperado de Stores y usuarios por negocio para decidir paginación desde F9?

## 19. Conclusión

La base multi-tienda F8 es correcta, segura y eficiente para el TPV actual: autorización N:M, contexto de una Store por token, revalidación inmediata y aislamiento tenant+Store con defensa en profundidad SQL. F9 debe completar la capa administrativa y sus invariantes, no reescribir el modelo. La primera subfase debe ser **F9.1 Contrato administrativo e invariantes**, seguida del provisionamiento atómico, porque el alta actual de nuevos negocios queda incompleta fuera del backfill histórico de Flyway.
