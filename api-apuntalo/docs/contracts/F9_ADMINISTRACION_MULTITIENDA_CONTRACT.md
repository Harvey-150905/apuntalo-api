# Contrato F9 — Administración multi-tienda

Fecha: 2026-07-22  
Estado: APROBADO para implementación F9.1–F9.8  
Modelo: **MANTENER MODELO ACTUAL**

## 1. Modelo

```text
Negocio
 ├── 1:N User          (rol global: SUPER_ADMIN | ADMIN | CAMARERO)
 └── 1:N Store

User N:M Store mediante UserStoreAccess
  - PK (user_id, store_id)
  - negocio_id tenant-safe
  - active (autorización; sin rol por Store)
  - User.default_store_id obligatorio y con acceso activo
```

Invariantes:

1. `User` pertenece a un único `Negocio`.
2. `Store` pertenece al mismo `Negocio`.
3. `UserStoreAccess` solo autoriza; no contiene rol.
4. `User.role` es global dentro del negocio.
5. Un usuario no puede ser ADMIN en una Store y CAMARERO en otra.
6. El JWT contiene una única Store activa (`storeId`).
7. El JWT no contiene la colección de Stores autorizadas.
8. Cada petición revalida usuario, negocio, rol, `tokenVersion`, acceso y Store contra PostgreSQL.
9. Los endpoints TPV no aceptan `tenantId`, `negocioId` ni `storeId`.
10. La única operación ordinaria que acepta `storeId` explícito es `POST /api/auth/switch-store`.
11. No se modifica el modelo JWT en F9.
12. No se modifican migraciones V8.

## 2. Matriz de roles

| Capacidad | SUPER_ADMIN | ADMIN | CAMARERO |
|---|---|---|---|
| Provisionar negocio | Sí (plataforma) | No | No |
| CRUD Stores (ámbito) | Cualquier tenant vía plataforma/target | Solo Stores con acceso activo | No |
| CRUD Users | Cross-tenant controlado | Solo usuarios administrables | No |
| Asignar User–Store | Sí | Solo Stores propias activas | No |
| Mesas/catálogo escritura | Sí (Store activa o target) | Store activa autorizada | No |
| Operar TPV | Sí (token) | Sí | Sí |
| Auditoría admin lectura | Sí | Ámbito permitido | No |

### Ámbito ADMIN sobre usuarios

Un ADMIN solo ve/gestiona a un usuario si **todas** las Stores activas del usuario ⊆ Stores activas del ADMIN.  
Si el usuario tiene alguna Store fuera del conjunto del ADMIN → fuera de listados o `404`/`403` según contrato del endpoint (listado: omitir; detalle/comando: `404 USER_NOT_ADMINISTRABLE` o `403`).

ADMIN no puede: crear/modificar/desactivar SUPER_ADMIN; asignar rol SUPER_ADMIN; mover tenant; escalarse; cambiar su propio rol; desactivarse; retirar su última Store activa.

## 3. Store Principal

- Única por negocio; creada en provisionamiento.
- Renombrable; editable en campos no estructurales.
- No eliminable; no desactivable; no cambia de tenant; no pierde `primaryStore`.
- Secuencia comercial creada en la misma TX.
- Store inicial del primer ADMIN.
- Sin operación F9 para “convertir secundaria en Principal”.

## 4. Borrado físico

Prohibido como operación ordinaria para Negocio, Store, User, Mesa/Subcategoría/Producto con historia.  
Estrategia: **activar / desactivar**.  
`DELETE` antiguos: bloqueados con código estable documentado (`PHYSICAL_DELETE_DISABLED`) o deprecados explícitamente; no convertir silenciosamente a soft-delete.

## 5. Usuario y Stores

Todo usuario operativo:

1. Un negocio.
2. Un rol global.
3. ≥1 acceso activo.
4. Store predeterminada activa del mismo tenant.
5. Acceso activo a la predeterminada.
6. No retirar el último acceso activo.
7. No dejar default sin acceso activo.
8. No asignar Store de otro tenant.
9. No cambiar tenant de User ni Store.

Al desactivar usuario: conservar asignaciones; `tokenVersion++`; bloquear si responsable de caja OPEN; permitir reactivación.

## 6. Retirada de acceso

- Bloquear último acceso activo → `LAST_ACTIVE_STORE_ACCESS`.
- Bloquear si caja OPEN en esa Store → `OPEN_CASH_SESSION_PREVENTS_ACCESS_REVOCATION` / `USER_HAS_OPEN_CASH_SESSION`.
- Si se retira la default → exigir `replacementDefaultStoreId` → `DEFAULT_STORE_REPLACEMENT_REQUIRED`.
- No incrementar `tokenVersion` por defecto (revalidación por petición invalida tokens de esa Store).
- Auditoría before/after sin secretos.

## 7. Caja abierta

Responsable de sesión OPEN:

- No puede `switch-store` → `OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH`.
- No retirar acceso a esa Store.
- No desactivar usuario.
- No desactivar Store.
- No transferir responsable en F9.

## 8. Tokens

- Role / password / active → `tokenVersion++`.
- Retirar acceso → invalida tokens de esa Store vía revalidación.
- Cambio de default → no invalida todos los tokens.
- Sin denylist JWT en F9.

## 9. Username / password

- Username único global; `trim` + minúsculas.
- BCrypt.
- Login sin tenant.
- Password administrativa mínima: **8** caracteres en provisionamiento/reset/alta admin.
- Nunca logs/auditoría/respuesta con password o hash.

## 10. Category

Sigue siendo **enum**. No migraciones de categorías. Solo subcategorías y productos administrables.

## 11. Semántica HTTP

| Código | Uso |
|---|---|
| 400 | DTO inválido / validación |
| 401 | Token inválido / no autenticado |
| 403 | Autenticado sin autoridad (`STORE_ACCESS_DENIED`, escalada) |
| 404 | Recurso no visible en scope |
| 409 | Conflicto estado/unicidad/referencias |
| 422 | Transición funcional imposible (`BusinessRuleException`) |

## 12. Catálogo de códigos funcionales (mínimo)

```text
PRIMARY_STORE_CANNOT_BE_DISABLED
STORE_HAS_OPEN_CASH_SESSION
STORE_HAS_OPEN_TICKETS
STORE_IS_USER_DEFAULT
STORE_ACCESS_DENIED
LAST_ACTIVE_STORE_ACCESS
DEFAULT_STORE_REPLACEMENT_REQUIRED
INVALID_DEFAULT_STORE
USER_HAS_OPEN_CASH_SESSION
OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH
OPEN_CASH_SESSION_PREVENTS_ACCESS_REVOCATION
ROLE_ESCALATION_NOT_ALLOWED
USER_NOT_ADMINISTRABLE
TENANT_PROVISIONING_FAILED
STORE_CODE_ALREADY_EXISTS
USERNAME_ALREADY_EXISTS
PHYSICAL_DELETE_DISABLED
```

Reutilizar códigos existentes semánticamente equivalentes; no crear sinónimos.

## 13. Auditoría administrativa

Acciones previstas (enums Java; scope TENANT o STORE):

- NEGOCIO_PROVISIONED, NEGOCIO_ACTIVATED, NEGOCIO_DEACTIVATED
- STORE_CREATED, STORE_UPDATED, STORE_ACTIVATED, STORE_DEACTIVATED
- USER_CREATED, USER_UPDATED, USER_ACTIVATED, USER_DEACTIVATED, USER_ROLE_CHANGED, USER_PASSWORD_RESET
- USER_STORE_ACCESS_ASSIGNED, USER_STORE_ACCESS_REACTIVATED, USER_STORE_ACCESS_REVOKED, USER_DEFAULT_STORE_CHANGED
- MESA_CREATED, MESA_UPDATED, MESA_ACTIVATED, MESA_DEACTIVATED
- SUBCATEGORY_*, PRODUCT_*

Entity types nuevos: `USER`, `STORE`, `USER_STORE_ACCESS`, `MESA`, `SUBCATEGORY`, `PRODUCT` (más `NEGOCIO` existente).

**Actor de plataforma:** `audit_events.user_id` es FK solo a `users(id)` (sin tenant compuesto). Un SUPER_ADMIN puede auditar en el tenant provisionado con su `user_id` real y `negocio_id` del tenant objetivo. No se fuerza FK `(user_id, negocio_id)`.

Nunca auditar: password, hash, JWT, Authorization, secretos, payloads completos sin filtrar.

## 14. Endpoints previstos

```text
POST   /api/platform/negocios/provision
PATCH  /api/platform/negocios/{id}/status

GET|POST|PUT|PATCH /api/admin/stores[...]
GET|POST|PUT|PATCH /api/admin/users[...]
GET|PUT|POST|DELETE /api/admin/users/{id}/stores[...]
PUT    /api/admin/users/{id}/default-store

Mesas / subcategorías / productos: endpoints existentes endurecidos (status, sin DELETE físico)
```

## 15. Casos A–H

| Caso | Contrato F9 |
|---|---|
| A 5 Stores exclusivas | CRUD Store + asignación |
| B ADMIN en 5 | Accesos N:M + rol global ADMIN |
| C CAMARERO 1 y 3 | Accesos + switch |
| D Cambio durante sesión | switch + guard caja OPEN |
| E Retirada JWT activo | Revocación por revalidación |
| F Desactivar Store | Guardas + historia |
| G Desactivar usuario | tokenVersion + caja |
| H Provisionamiento | Endpoint atómico plataforma |

## 16. Concurrencia — orden de locks

```text
Negocio (si aplica)
→ User
→ Store
→ UserStoreAccess
→ CashSession (consulta/lock si guarda)
```

No contradecir locks productivos de tickets/caja; alinear cuando haya solape. Constraints SQL = defensa final.

## 17. Fuera de alcance F9.1–F9.8

- F9.9 smoke completo y regresión 48 F8 como cierre GO.
- Roles por Store, JWT multi-store, transferencia de caja, Category entidad, Observabilidad F12.
