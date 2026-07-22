# Implementación F9 — Administración multi-tienda

Fecha: 2026-07-23
Estado: F9.1–F9.8 implementadas. Pendiente F9.9 (smoke completo + regresión 48 F8 + declaración GO).
Referencia de contrato: `docs/contracts/F9_ADMINISTRACION_MULTITIENDA_CONTRACT.md`

Este documento describe, subfase por subfase, lo que se implementó en el
código, para que quien continúe con F9.9 pueda verificar cobertura sin tener
que releer todos los diffs.

## 1. Resumen de subfases

| Subfase | Alcance | Estado |
|---|---|---|
| F9.1 | `AdminAuthorizationService` / `AdminAuthorizationRules` | Hecho (previo) |
| F9.2 | `TenantProvisioningService` (provisionamiento atómico de negocio) | Hecho (previo) |
| F9.3 | `StoreAdminService` / `StoreAdminController` (CRUD Stores) | Hecho (previo) |
| F9.4 | `UserAdminService` / `UserAdminController` | Hecho (previo) |
| F9.5 | `UserStoreAssignmentService/Controller`, `PlatformNegocioController`, rutas `SecurityConfig` | Hecho (previo) |
| F9.6 | Mesas: auditoría + activar/desactivar + borrado físico deshabilitado | **Hecho en esta iteración** |
| F9.7 | Catálogo (Subcategorías/Productos): auditoría + activar/desactivar | **Hecho en esta iteración** |
| F9.8 | Guard de caja abierta en `switch-store`; verificación `NegocioService`/`SecurityConfig` | **Hecho en esta iteración** |
| F9.9 | Smoke E2E + regresión 48 casos F8 + declaración GO | Fuera de alcance de esta iteración |

## 2. F9.6 — Mesas

### Cambios

- `MesaService`
  - Ahora es transaccional método a método (`@Transactional` en escritura,
    `@Transactional(readOnly = true)` en lectura).
  - Inyecta `AuditEventService` y `TicketRepository` (además de los
    repositorios/existentes `MesaRepository`, `NegocioRepository`,
    `ActiveStoreContext`).
  - `create()` registra `AuditAction.MESA_CREATED` (`AuditEntityType.MESA`)
    tras persistir.
  - `update()` registra `AuditAction.MESA_UPDATED` con snapshot
    before/after (`numero`, `activa`).
  - Nuevo método `setActive(Long id, boolean active)`:
    - Carga la mesa con bloqueo pesimista
      (`findByIdAndNegocioIdAndStoreIdForUpdate`, ya existía en el
      repositorio).
    - Si `active == false` y la mesa está `OCCUPIED` **o** existe un ticket
      `OPEN` asociado (`ticketRepository.existsByMesaIdAndNegocioIdAndStoreIdAndStatus`,
      ya existía en `TicketRepository`), lanza
      `ConflictException("MESA_HAS_OPEN_TICKET", ...)` → HTTP 409.
    - Si no hay cambio de estado, es idempotente (no re-audita).
    - Registra `MESA_ACTIVATED` / `MESA_DEACTIVATED` según corresponda.
  - `delete(Long id)` queda **deprecado** y siempre lanza
    `ConflictException("PHYSICAL_DELETE_DISABLED", "Usa desactivación")`
    → HTTP 409. Ya no realiza soft-delete silencioso.

- `MesaController`
  - Nuevo `PATCH /api/mesas/{id}/status` con `ActiveStatusRequestDto`
    (`{ "active": boolean }`), delega en `mesaService.setActive(...)`.
  - `DELETE /api/mesas/{id}` se conserva (mismo path/verbo) pero ahora
    siempre responde 409 `PHYSICAL_DELETE_DISABLED` en vez de desactivar
    silenciosamente.

### Código funcional nuevo

```text
MESA_HAS_OPEN_TICKET        (409) — desactivar mesa OCCUPIED / con ticket OPEN
PHYSICAL_DELETE_DISABLED    (409) — DELETE físico de mesa
```

No se requirió ninguna migración: `MesaRepository` ya tenía
`findByIdAndNegocioIdAndStoreIdForUpdate` y `TicketRepository` ya tenía
`existsByMesaIdAndNegocioIdAndStoreIdAndStatus`.

## 3. F9.7 — Catálogo (Subcategorías y Productos)

### Migración

`V9_1__subcategory_active_flag.sql` (primera migración de la Fase 9; **no
modifica ninguna migración V8**):

```sql
ALTER TABLE subcategories ADD COLUMN activo boolean NOT NULL DEFAULT true;
CREATE INDEX idx_subcategories_store_active_name ON subcategories(negocio_id, store_id, activo, nombre, id);
```

`Subcategory` (entidad) gana el campo `activo` (`Boolean`, `@Builder.Default = true`),
igual que `Product.activo` ya existente.

### SubcategoryService

- Inyecta `AuditEventService`.
- `save()` (create): setea `activo` (default `true` si no viene en el DTO),
  audita `SUBCATEGORY_CREATED`.
- `update()`: audita `SUBCATEGORY_UPDATED` con before/after (`nombre`,
  `category`).
- `deleteById()` **deja de borrar físicamente**: si la subcategoría ya
  estaba inactiva, es un no-op; si estaba activa, la desactiva
  (`activo = false`) y audita `SUBCATEGORY_DEACTIVATED`. Método marcado
  `@Deprecated` en favor de `setActive`.
- Nuevo `setActive(Long id, boolean active)`: activa/desactiva y audita
  `SUBCATEGORY_ACTIVATED` / `SUBCATEGORY_DEACTIVATED`. Idempotente si no
  hay cambio de estado.

`SubcategoryRequestDto` y `SubcategoryResponseDto` ganan el campo
`activo` (opcional en el request; en el create, `null` ⇒ `true`).

### SubcategoryController

- Nuevo `PATCH /api/subcategories/{id}/status` con `ActiveStatusRequestDto`.
- `DELETE /api/subcategories/{id}` se conserva pero ahora invoca la
  desactivación (no elimina la fila).

### ProductService

- Inyecta `AuditEventService`.
- `save()` (create): audita `PRODUCT_CREATED` (`name`, `price`, `activo`).
- `update()`: audita `PRODUCT_UPDATED` con before/after (`name`, `price`,
  `activo`).
- Nuevo `setActive(Long id, boolean active)`: activa/desactiva; al
  desactivar libera la imagen de Cloudinary (mismo comportamiento que el
  `delete()` histórico) y audita `PRODUCT_ACTIVATED` / `PRODUCT_DEACTIVATED`.
  Idempotente si no hay cambio de estado.
- `delete(Long id)` queda **deprecado** y delega en `setActive(id, false)`
  (se conserva el soft-delete existente vía el endpoint `DELETE`, pero
  ahora queda auditado con `PRODUCT_DEACTIVATED`).

### ProductController

- Nuevo `PATCH /api/products/{id}/status` con `ActiveStatusRequestDto`.

No se requiere código funcional nuevo en `products` (reutiliza
`activo`/soft-delete ya existente); en `subcategories` se introduce el
concepto por primera vez, alineado con `products`.

## 4. F9.8 — Guard de caja abierta en switch-store

### AuthService

- Inyecta `CashSessionRepository`.
- `switchStore(Long requestedStoreId)`: antes de resolver la Store
  destino, comprueba
  `cashSessionRepository.existsByOpenedByIdAndNegocioIdAndStatus(userId, tenantId, CashSessionStatus.OPEN)`.
  Si el usuario autenticado es responsable de una sesión de caja `OPEN` en
  cualquier Store del tenant, lanza
  `ConflictException("OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH", ...)` →
  HTTP 409, **antes** de tocar `UserStoreAccessService`/JWT.
- Alineado con el contrato F9 §7 ("Responsable de sesión OPEN ... No puede
  `switch-store`").

### Verificaciones (sin cambios de código)

- `NegocioService.setActive(Long, boolean)` **ya existía** (implementado en
  una subfase previa): bloquea con `NEGOCIO_HAS_OPEN_CASH_SESSIONS` /
  `NEGOCIO_HAS_OPEN_TICKETS`, audita `NEGOCIO_ACTIVATED` /
  `NEGOCIO_DEACTIVATED`. `deleteById()` ya lanzaba
  `NEGOCIO_PHYSICAL_DELETE_DISABLED`. No requirió cambios.
- `SecurityConfig` **ya tenía** las rutas de plataforma/administración
  (`/api/platform/**` solo `SUPER_ADMIN`; `/api/admin/stores/**`,
  `/api/admin/users/**` `SUPER_ADMIN`/`ADMIN`) y las rutas de
  mesas/productos/subcategorías con los roles correctos para cubrir los
  nuevos endpoints `PATCH .../status` (matchers `"/api/mesas/**"`,
  `"/api/products/**"`, `"/api/subcategories/**"` ya existentes cubren el
  nuevo verbo/subpath). No requirió cambios.

### Código funcional nuevo

```text
OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH   (409) — switch-store con caja OPEN a cargo del usuario
```

## 5. Auditoría — resumen de acciones nuevas usadas en esta iteración

Todas ya estaban declaradas en `AuditAction`/`AuditEntityType` (Fase 9,
subfases previas), esta iteración es la primera en emitirlas:

```text
MESA_CREATED, MESA_UPDATED, MESA_ACTIVATED, MESA_DEACTIVATED
SUBCATEGORY_CREATED, SUBCATEGORY_UPDATED, SUBCATEGORY_ACTIVATED, SUBCATEGORY_DEACTIVATED
PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_ACTIVATED, PRODUCT_DEACTIVATED
```

Todas con `scopeType = STORE` (se auditan con la Store activa del
usuario autenticado, vía `AuditEventService.recordSuccess(...)`, no
`recordSuccessForTenant(...)`: son operaciones ordinarias del usuario
dentro de su propio tenant/Store, no acciones cross-tenant de plataforma).

## 6. Endpoints nuevos

```text
PATCH /api/mesas/{id}/status           { "active": boolean } -> MesaResponseDto
PATCH /api/subcategories/{id}/status   { "active": boolean } -> SubcategoryResponseDto
PATCH /api/products/{id}/status        { "active": boolean } -> ProductResponseDto
```

Los tres reutilizan `ActiveStatusRequestDto` (ya existente desde F9.3) y
quedan cubiertos por los matchers de seguridad ya vigentes para sus
recursos (`hasAnyRole("SUPER_ADMIN", "ADMIN")`).

## 7. Pruebas

`src/test/java/.../service/AdminAuthorizationRulesTest.java` (nuevo):
17 casos unitarios puros (sin contexto Spring) sobre
`AdminAuthorizationRules`:

- `isSubset`: conjunto vacío siempre es subconjunto; superset nulo rechaza
  subconjunto no vacío.
- `canAdminister`: SUPER_ADMIN siempre puede; CAMARERO nunca puede; ADMIN
  no puede administrar a un SUPER_ADMIN; ADMIN puede administrar cuando
  las Stores del objetivo ⊆ Stores propias; ADMIN no puede si el objetivo
  tiene una Store fuera de su ámbito.
- `isDefaultStoreInActiveSet`: default debe pertenecer al conjunto activo;
  inválido con conjunto vacío/nulo o default nulo.
- `canRevokeKeepingAtLeastOne`: bloquea retirar la última Store activa;
  permite si queda ≥1; es no-op seguro si la Store a retirar no está
  activa.
- `isRoleChangeAllowed` (escalada de rol): SUPER_ADMIN siempre puede;
  CAMARERO nunca puede; ADMIN no puede escalar a SUPER_ADMIN ni modificar
  a un SUPER_ADMIN existente; ADMIN sí puede alternar ADMIN ⇄ CAMARERO.

`ApiApuntaloApplicationTests` (existente): normalización de username sin
cambios.

No se añadieron tests de integración (`@SpringBootTest`/MockMvc) para
Mesas/Catálogo/AuthService en esta iteración; quedan para F9.9 (smoke +
regresión), donde `scripts/smoke-fase9-admin.ps1` ejercita los flujos HTTP
reales contra una instancia levantada.

## 8. Archivos tocados en esta iteración

```text
src/main/java/.../service/MesaService.java                (modificado)
src/main/java/.../controller/MesaController.java           (modificado)
src/main/java/.../entity/Subcategory.java                  (modificado)
src/main/java/.../dto/SubcategoryRequestDto.java            (modificado)
src/main/java/.../dto/SubcategoryResponseDto.java           (modificado)
src/main/java/.../service/SubcategoryService.java           (modificado)
src/main/java/.../controller/SubcategoryController.java     (modificado)
src/main/java/.../service/ProductService.java                (modificado)
src/main/java/.../controller/ProductController.java          (modificado)
src/main/java/.../service/AuthService.java                   (modificado)
src/main/resources/db/migration/V9_1__subcategory_active_flag.sql (nuevo)
src/test/java/.../service/AdminAuthorizationRulesTest.java   (nuevo)
docs/implementation/F9_ADMINISTRACION_MULTITIENDA_IMPLEMENTATION.md (nuevo, este archivo)
scripts/smoke-fase9-admin.ps1                                 (nuevo, preparado para F9.9)
```

No se modificó ninguna migración `V8_*`. No se modificó el contrato
(`docs/contracts/F9_ADMINISTRACION_MULTITIENDA_CONTRACT.md`): sigue
vigente sin cambios; esta implementación es conforme a él.

## 9. Pendiente para F9.9 (fuera de alcance de esta iteración)

- Ejecutar `scripts/smoke-fase9-admin.ps1` contra una instancia real
  (API levantada + Postgres con migraciones aplicadas) y confirmar PASS.
- Regresión completa de los 48 casos de Fase 8 (no se ejecuta ni se
  declara aquí).
- Declaración formal de **FASE 9 GO** (explícitamente fuera de alcance:
  no se declara en esta iteración ni se hace commit/push).
