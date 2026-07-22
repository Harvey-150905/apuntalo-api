# F9.9 — Resultados de Smoke y cierre de Fase 9

Fecha de ejecución: 2026-07-23
Entorno: API local en `http://127.0.0.1:8080` (PostgreSQL `apuntalo`, Flyway en versión **9.1**).
Actor de plataforma: `harbey` (SUPER_ADMIN).

> Nota de seguridad: ninguna credencial se ha escrito en el repositorio. Todas
> se pasan por parámetro al script y `$env:PGPASSWORD` solo se fija en memoria
> del proceso durante cada consulta psql y se limpia al terminar.

## 1. Veredicto

| Comprobación | Resultado |
|---|---|
| Smoke F9.9 (bloques A–K) | **79 PASS / 0 FAIL / 0 SKIP** |
| Regresión Fase 8 (48 casos) | **PASS 48/48** |
| `mvnw clean verify` (compile + test + verify) | **BUILD SUCCESS** — 199 fuentes, 18 tests, 0 fallos/errores/omitidos |
| Migraciones V8 modificadas | **Ninguna** (solo `V9_1__subcategory_active_flag.sql` es nueva) |
| Cambios en JWT / modelo / V8 | **Ninguno** |

### VEREDICTO FASE 9: **GO**

## 2. Cobertura del smoke F9.9

El script `scripts/smoke-fase9-admin.ps1` cubre los bloques A–K. Cada aserción
valida el código HTTP y, cuando aplica, el código funcional de error del
catálogo F9. No hay SKIP crítico contabilizado como PASS.

| Bloque | Contenido | Estado |
|---|---|---|
| **A. Preflight** | login SUPER_ADMIN 200; Flyway=9.1; `subcategories.activo` existe; secuencias `mesas/subcategories/products/stores/users` alineadas (last_value ≥ MAX(id)). | PASS |
| **B. Provisionamiento** | `POST /api/platform/negocios/provision` → 201 con `negocioId`, `principalStore`, `admin{id,username,defaultStoreId}`, sin password en la respuesta; login del ADMIN provisionado con Store activa = Principal. | PASS |
| **C. CRUD Stores** | alta de 4 secundarias (5 en total), listado por `negocioId`, `STORE_CODE_ALREADY_EXISTS` en código duplicado, `PUT` de edición, `PATCH .../status` desactivar/reactivar. | PASS |
| **D. Usuarios + asignación** | alta de CAMARERO y de ADMIN limitado; `ROLE_ESCALATION_FORBIDDEN` (ADMIN no crea SUPER_ADMIN); asignación batch N:M; `DEFAULT_STORE_NOT_IN_ACTIVE_SET`; `switch-store` autorizado (200) y `STORE_ACCESS_DENIED` a tienda no autorizada. | PASS |
| **E. Mesas** | alta; desactivación de mesa libre; `MESA_HAS_OPEN_TICKET` al desactivar mesa con ticket OPEN; `PHYSICAL_DELETE_DISABLED` en DELETE físico. | PASS |
| **F. Catálogo** | subcategoría (status + `DELETE` que desactiva sin borrar); producto vía `multipart/form-data` (parte `product` JSON) + `PATCH .../status`. | PASS |
| **G. TPV** | crear caja; abrir sesión; crear ticket con `originCashSessionId`; añadir líneas; cobro con `payments:[{method:CASH,amount,cashReceived}]`. | PASS |
| **H. switch-store con caja** | `OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH` con sesión de caja abierta. | PASS |
| **I. Desactivar Store** | `STORE_IS_PRIMARY_CANNOT_DISABLE`; `STORE_HAS_OPEN_CASH_SESSIONS`; `STORE_IS_DEFAULT_OF_ACTIVE_USER`. | PASS |
| **J. Accesos + usuario** | `LAST_ACTIVE_STORE_ACCESS`; `REPLACEMENT_DEFAULT_STORE_REQUIRED`; `USER_HAS_OPEN_CASH_SESSION` (retiro de acceso y desactivación); desactivación de usuario con invalidación del token anterior (tokenVersion++ → 401). | PASS |
| **K. Cross-tenant / IDOR + Auditoría** | provisión de tenant B; `TENANT_SCOPE_FORBIDDEN` (ADMIN A hacia negocio B); Store de B invisible para A (404); ADMIN limitado sin autoridad → `STORE_AUTHORITY_REQUIRED`; auditoría accesible con eventos del tenant; verificación SQL de ausencia de secretos (password/hash `$2a/$2b`/jwt/accessToken) en los JSON de auditoría → 0 coincidencias. | PASS |

Limpieza: se cobra el ticket abierto, se cierran las sesiones de caja, se
desactivan las entidades QA (mesa/producto/subcategoría) y los negocios QA
provisionados. No se realizan borrados físicos de historia.

## 3. Códigos funcionales verificados (observados en ejecución)

```
STORE_CODE_ALREADY_EXISTS            ROLE_ESCALATION_FORBIDDEN
DEFAULT_STORE_NOT_IN_ACTIVE_SET      STORE_ACCESS_DENIED
MESA_HAS_OPEN_TICKET                 PHYSICAL_DELETE_DISABLED
OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH
STORE_IS_PRIMARY_CANNOT_DISABLE      STORE_HAS_OPEN_CASH_SESSIONS
STORE_IS_DEFAULT_OF_ACTIVE_USER      LAST_ACTIVE_STORE_ACCESS
REPLACEMENT_DEFAULT_STORE_REQUIRED   USER_HAS_OPEN_CASH_SESSION
TENANT_SCOPE_FORBIDDEN               STORE_AUTHORITY_REQUIRED
```

## 4. Regresión Fase 8

`smoke-fase8-corregido.ps1` ejecutado contra `127.0.0.1:8080` con los usuarios
QA existentes (`qa_admin_test`, `qa_camarero_test`): **RESULTADO PASS (48
comprobaciones)** — aislamiento de cajas por Store, idempotencia por Store,
unicidad tenant-wide del responsable de caja (`USER_ALREADY_RESPONSIBLE_FOR_OPEN_SESSION`),
aislamiento de auditoría y switch-store del camarero.

## 5. Build y control de cambios

- `mvnw clean verify`: **BUILD SUCCESS** (compilación de 199 fuentes; 18 tests
  ejecutados, 0 fallos, 0 errores, 0 omitidos; empaquetado del jar).
- No se han editado migraciones V8; la única migración F9 presente es
  `V9_1__subcategory_active_flag.sql` (ya existente en el árbol de trabajo).
- No se han modificado el modelo JWT ni entidades/servicios de dominio como
  parte de F9.9: los cambios de código Java en `git status` corresponden a la
  implementación F9.1–F9.8 previa ya presente en el árbol de trabajo (no
  commiteada). En F9.9 solo se ha (re)generado el script de smoke y este
  documento de verificación.
- Sin commit ni push (según el alcance de la tarea).

## 6. Observaciones

- Las sesiones de caja sin conciliación (`reconciliationRequired=false`) se
  cierran sin `countedCash`; enviarlo produce `COUNTED_CASH_NOT_ALLOWED` (422).
  El script F9.9 respeta esta regla.
- El provisionamiento crea negocios reales no borrables; el smoke los deja
  **desactivados** al finalizar, conforme a la política de no borrado físico.
