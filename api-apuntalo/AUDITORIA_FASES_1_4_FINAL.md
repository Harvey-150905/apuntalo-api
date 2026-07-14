# Auditoría final — Fases 1–4

Fecha: 2026-07-15. Alcance: autenticación/multi-tenant, tickets, concurrencia e idempotencia. No incluye fases 5+.

## 1. Resumen ejecutivo

| Fase | Estado | Resultado |
|---|---|---|
| 1 | Implementada, pendiente de integración | Username global normalizado, JWT con tenant/version, principal validado contra BD y consultas tenant-scoped. |
| 2 | Implementada, pendiente de integración | Mutaciones solo en OPEN, total recalculado desde líneas activas, pago/cancelación/mesa atómicos. |
| 3 | Implementada, pendiente de carga concurrente real | Locks pesimistas tenant-scoped, `@Version` en Ticket/Mesa e índice parcial. |
| 4 | Implementada, pendiente de integración real | Hash estable, scope tenant+usuario+operación+key, replay y transacción única negocio/registro. |

El trabajo anterior cubría gran parte del código. Se corrigieron credenciales embebidas, salida de contraseña al arranque, 401 de seguridad, validación de `sub`, límite transaccional de idempotencia y peligros de la migración. Veredicto real: compila y los tests disponibles pasan; no puede declararse verificado sin PostgreSQL configurado y las pruebas concurrentes/smoke.

## 2. Archivos creados

| Archivo | Finalidad | Fase |
|---|---|---|
| `dto/IdempotentOutcome.java`, `entity/IdempotencyRecord.java`, `entity/enums/IdempotencyStatus.java` | Contrato y persistencia idempotente | 4 |
| `repository/IdempotencyRecordRepository.java`, `service/IdempotencyRecordService.java`, `service/IdempotencyService.java` | Flujo idempotente | 4 |
| `security/AuthenticatedUserPrincipal.java`, `security/CurrentUser.java`, `security/TokenPrincipalResolver.java` | Principal central y validación contra BD | 1 |
| `dto/MeResponseDto.java` | Respuesta autenticada sin password | 1 |
| `exception/BadRequestException.java`, `BusinessRuleException.java`, `ConflictException.java` | Errores HTTP coherentes | 2–4 |
| `resources/sql/phase-1-4-migration.sql` | Migración PostgreSQL manual | 1–4 |
| `AUDITORIA_FASES_1_4_FINAL.md` | Evidencia y entrega | 1–4 |

## 3. Archivos modificados

| Grupo | Cambio | Motivo |
|---|---|---|
| `ApiApuntaloApplication`, seguridad, auth/JWT | Eliminación de `System.out`, principal BD, claims, 401 JSON, Basic/form deshabilitados | Seguridad |
| DTO de login/usuarios/tickets/productos | Validación y contratos sin tenant operativo ni totales cliente | Integridad |
| User/Ticket/Mesa y repositorios | Username, activo/versiones y consultas/locks por tenant | Fases 1/3 |
| `TicketController`, `TicketService` | Idempotencia y reglas transaccionales de tickets | Fases 2/4 |
| excepciones | 400/401/404/409/422 y fallback 500 sin filtrar detalle | Contrato API |
| `application.properties` | Variables de entorno, `validate`, OIV desactivado y sin secretos | Producción segura |
| test existente | Prueba autónoma de normalización | Evitar dependencia de credenciales locales |

El ZIP binario preexistente continúa modificado; no se revirtió ni se editó deliberadamente.

## 4. Decisiones técnicas

- Username: se persiste con `trim().toLowerCase()` y se busca ignore-case; SQL crea índice único global `lower(username)` tras abortar si hay colisiones.
- Tenant: siempre procede de `AuthenticatedUserPrincipal`, construido desde `user.negocio.id`; nunca de DTO operativo.
- JWT: HMAC, expiración y claims `sub/userId/username/role/tenantId/tokenVersion`; `sub` debe coincidir con `userId`.
- `TokenPrincipalResolver`: `@Transactional(readOnly=true)` carga usuario/negocio y contrasta activo, tenant, rol y versión sin Open EntityManager in View.
- Aislamiento: recursos operativos se consultan por id+tenant; ids ajenos producen 404. SUPER_ADMIN conserva endpoints globales, sin selector tenant para tickets.
- Tickets: total único desde subtotales activos; vacío solo se rechaza al pagar. Batch es el máximo serializado bajo lock del ticket + 1.
- Concurrencia: `PESSIMISTIC_WRITE` en ticket/mesa dentro de transacciones; mesas se bloquean por id ascendente; `@Version` complementa la defensa.
- Ticket abierto: índice parcial único por `mesa_id`; se omite tenant porque `mesa.id` es global y FK del ticket ya identifica una sola mesa.
- Idempotencia: SHA-256 de JSON con propiedades/mapas ordenados; scope `(tenant,user,operation,key)`, `response_body TEXT`, replay con status original e `Idempotency-Replayed: true`.
- Transacción idempotente: registro PROCESSING, negocio y COMPLETED comparten una transacción. Un fallo revierte todo; no puede quedar negocio confirmado sin registro ni registro completado sin negocio. `FAILED` queda reservado para recuperación/importación, no se fuerza con una transacción separada insegura.
- Conflictos: locks/integridad/estado concurrente → 409; una petición posterior normal sobre PAID/CANCELLED → 422.

## 5. Endpoints finales

Todos salvo login y público requieren Bearer JWT. Los endpoints tenant obtienen tenant del principal.

| Métodos/rutas | Roles | Idempotency-Key | DTO / respuesta | HTTP principal |
|---|---|---|---|---|
| POST `/api/auth/login`; GET `/api/auth/me` | público; autenticado | no | LoginRequest→LoginResponse; MeResponse | 200/400/401 |
| GET/POST/PUT/DELETE `/api/users[/{id}]` | GET ADMIN/SUPER_ADMIN; mutación SUPER_ADMIN | no | User DTO | 200/201/204/404/409 |
| GET/POST/PUT/DELETE `/api/negocios[/{id}]` | GET ADMIN/SUPER_ADMIN; mutación SUPER_ADMIN | no | Negocio DTO | 200/201/204/404 |
| GET `/api/public/negocios` | público | no | lista Negocio | 200 |
| GET/POST/PUT/DELETE `/api/subcategories[/{id}]` | ADMIN/SUPER_ADMIN | no | Subcategory DTO | 200/201/204/404/409 |
| GET `/api/products/**`; mutaciones `/api/products/**` | GET incluye CAMARERO; mutación ADMIN/SUPER_ADMIN | no | Product DTO/multipart | 200/201/204/400/404/409 |
| GET `/api/mesas/**`; mutaciones `/api/mesas/**` | GET incluye CAMARERO; mutación ADMIN/SUPER_ADMIN | no | Mesa DTO | 200/201/204/404/409 |
| POST `/api/tickets` | CAMARERO/ADMIN/SUPER_ADMIN | sí en configuración actual | TicketRequest→TicketResponse | 201/400/404/409/422 |
| POST `/{ticketId}/lines`, POST `/{ticketId}/pay` | CAMARERO/ADMIN/SUPER_ADMIN | sí | AddLines/Pay→Ticket DTO | 200/400/404/409/422 |
| PATCH `/{ticketId}/lines/{lineId}/cancel`, `/batches/{batch}/cancel`, `/{ticketId}/cancel`, `/{ticketId}/mesa` | CAMARERO/ADMIN/SUPER_ADMIN | sí | path/ChangeMesa→TicketDetail | 200/400/404/409/422 |
| PATCH `/{ticketId}/notes` | autenticado por fallback | no | UpdateNotes→TicketDetail | 200/404/422 |
| GET `/api/tickets/{id}`, `/mesa/{id}` | CAMARERO/ADMIN/SUPER_ADMIN | no | Ticket DTO | 200/404 |
| GET `/api/tickets/paid[/*]` | según reporte; user-summary restringido ADMIN | no | páginas/resúmenes | 200/400 |
| GET `/api/tickets/open`, `/cancelled`, `/cash-closing` | ADMIN/SUPER_ADMIN | no | páginas/resumen | 200/400 |

Errores comunes: `INVALID_CREDENTIALS`, `INVALID_TOKEN`, `RESOURCE_NOT_FOUND`, `TICKET_ALREADY_PAID`, `TICKET_ALREADY_CANCELLED`, `TABLE_ALREADY_OCCUPIED`, `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST`, `IDEMPOTENCY_REQUEST_IN_PROGRESS`.

## 6. Migración

Añade/backfill `users.activo/token_version`, normaliza usernames, detecta colisiones antes de mutar, elimina únicamente unique antigua `(negocio_id,username)`, crea unique case-insensitive, versiones, auditoría de cancelación, índice OPEN e idempotencia con TEXT/FKs/check/índices. Es reejecutable y no borra datos. Ejecutar con backup mediante `psql ... -v ON_ERROR_STOP=1 -f src/main/resources/sql/phase-1-4-migration.sql`; revisar las consultas finales. Riesgo: duplicados de username o tickets OPEN duplicados abortarán y requieren resolución manual no destructiva.

## 7. Pruebas

| Categoría/caso | Resultado | Evidencia | Estado BD |
|---|---|---|---|
| Compilación | PASS | `mvnw compile`: BUILD SUCCESS | N/A |
| Unitario username | PASS | 1 test, 0 failures/errors | N/A |
| Clean verify/JAR | PASS | BUILD SUCCESS, JAR generado | N/A |
| Arranque con PostgreSQL sin secreto | FAIL esperado | SCRAM pidió password; confirma ausencia de credencial embebida | Sin conexión/cambios |
| Integración, smoke, seguridad, tenant | NO EJECUTADO | faltan DB_PASSWORD/JWT_SECRET de entorno | No tocada |
| Concurrencia e idempotencia real | NO EJECUTADO | no hubo BD autenticable | No tocada |
| Datos QA | NO INSPECCIONADOS | no hubo conexión autorizada | No se borró nada |

## 8. Compatibilidad frontend

Se mantienen rutas y DTO principales; login sigue enviando solo `username/password` y nunca tenant. El frontend debe enviar una clave nueva por intención y reutilizarla al reintentar en crear ticket, añadir líneas, pagar, cancelar ticket/línea/batch y cambiar mesa. Formato recomendado: UUID v4 (máximo 100 caracteres). Debe manejar 400 por key ausente/inválida, 409 por reutilización/body distinto o concurrencia, 422 por estado de negocio y la cabecera de replay. Crear conserva 201 también al reproducirse.

## 9. Riesgos

- Bloqueantes para declarar verificado: migración y suites smoke/concurrencia no ejecutadas contra PostgreSQL.
- Altos: cobertura automática actual mínima; recuperación temporal de PROCESSING no tiene job programado.
- Medios: métodos legacy `SecurityUtils` permanecen por compatibilidad; warning de API JWT deprecada; CORS contiene una IP local fija.
- Futuro: fases 5+, scheduler de retención y suite Testcontainers. No se implementaron.

## 10. Veredicto

**FASES 1–4 INCOMPLETAS**

El código de las cuatro fases está consolidado y compilable, pero falta la verificación de base de datos exigida; no se afirma preparación completa para producción.

## 11. Resultado de comandos

- `./mvnw compile`: BUILD SUCCESS.
- `./mvnw test`: BUILD SUCCESS; 1 test, 0 fallos, 0 errores.
- `./mvnw clean verify`: BUILD SUCCESS; build limpio, test y empaquetado completados.

## 12. Git diff

Consultar el estado vivo con `git status --short` y `git diff --stat`. No se hizo commit ni push.

## Validación real Fases 2–4

Revisión del 2026-07-15. Los stack traces de `backend-login.log` confirman los dos errores observados con `open-in-view=false`.

| Prueba | HTTP/resultados | Estado BD | PASS/FAIL |
|---|---:|---|---|
| `GET /api/products/activos` (ejecución previa) | 500; `LazyInitializationException` al leer `Subcategory.category` en `ProductService.mapToResponseDto` | Sin cambios | FAIL confirmado |
| Corrección productos | Mapper cubierto por `@Transactional(readOnly=true)` en las tres consultas | No ejecutada contra BD tras el cambio | Pendiente |
| `GET /api/tickets/6` y `/7` (ejecución previa) | 500; `LazyInitializationException` al leer `Mesa.numero` en `TicketService.toDetailResponse` | Tickets no modificados | FAIL confirmado |
| Corrección detalle | `findDetailById` cubierto por `@Transactional(readOnly=true)` y conserva filtro id+tenant | No ejecutada contra BD tras el cambio | Pendiente |
| Compile/test tras corrección | BUILD SUCCESS; 1 test, 0 fallos | N/A | PASS |
| Ciclo Fase 2 | No ejecutado: no existe `DB_PASSWORD`/`JWT_SECRET` en proceso, usuario ni máquina | No tocada | FAIL por no ejecutada |
| Concurrencia Fase 3 | No ejecutada por el mismo bloqueo de entorno | No tocada | FAIL por no ejecutada |
| Idempotencia Fase 4 | No reejecutada tras la corrección | No tocada | FAIL por no ejecutada |

**FASE 2 NO VERIFICADA**

**FASE 3 NO VERIFICADA**

**FASE 4 NO VERIFICADA**

No se crearon, borraron ni alteraron datos QA durante esta revisión. El proceso previo ya no está activo y no hay listener en el puerto 8080.

### Reanudación con PostgreSQL disponible

Tras proporcionar la credencial local se arrancó la aplicación con un JWT aleatorio efímero y `ddl-auto=validate`.

| Prueba | HTTP/resultados | Estado final | PASS/FAIL |
|---|---:|---|---|
| Productos activos tras corrección | 200 | Lista serializada sin proxies cerrados | PASS |
| Detalle tickets 6 y 7 tras corrección | 200/200 | Detalles serializados | PASS |
| Crear ticket QA 8 + replay | 201/201; replay `true`, mismo ID | Ticket 8 creado una vez | PASS |
| Añadir cantidad 2 + replay | 200/200; replay `true` | Una línea, sin duplicación | PASS |
| Pagar + replay | 200/200; replay `true` | Ticket 8 `PAID`, total 42 | PASS |

Estos resultados amplían la evidencia, pero no sustituyen las pruebas obligatorias todavía pendientes de cancelación completa, SQL directo y matriz de concurrencia. Por ello se mantienen los tres veredictos **NO VERIFICADA**.
