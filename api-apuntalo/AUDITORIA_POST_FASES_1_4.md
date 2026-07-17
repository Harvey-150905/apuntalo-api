# Auditoría final rápida — Fase 5

Fecha: 2026-07-17  
Resultado: **GO**

**FASE 5 COMPLETAMENTE IMPLEMENTADA Y VERIFICADA**

## Resultados

1. **Flyway:** compatible con Spring Boot 4.0.4 y PostgreSQL; validó 3 migraciones. `baseline-on-migrate=false`, `clean-disabled=true` y Hibernate en `validate`. No hay migraciones de Fase 5 duplicadas activas en `resources/sql`.
2. **Historial:** rank 1, versión 4, `Baseline phases 1-4`, BASELINE, success=true; rank 2, versión 5.1, `numbering and discounts`, checksum 1002929920, success=true; rank 3, versión 5.2, `audit events`, checksum 1029628022, success=true.
3. **Segundo arranque:** esquema actual 5.2; `Schema "public" is up to date. No migration necessary.` No se añadió baseline ni cambió el conteo previo de datos.
4. **Hibernate validate:** correcto; la aplicación mostró `Started ApiApuntaloApplication`.
5. **Backfill/esquema:** tablas y columnas requeridas presentes; PAID sin `commercial_number`: 0; duplicados por negocio/número: 0; secuencias iguales a `MAX(commercial_number) + 1`.
6. **Numeración:** tickets 63 y 62 pagados en ese orden; números 22 (`000022`) y 23 (`000023`), únicos y con seis dígitos.
7. **Replay de pago:** HTTP 200, cuerpo idéntico, mismo número, `Idempotency-Replayed=true`, secuencia sin incremento adicional y sin eventos duplicados.
8. **Descuento:** HTTP 200; base 42.00, 10 %, importe 4.20, subtotal y total 37.80, aplicado por `harbey` y fecha informada.
9. **Retirada:** HTTP 200; subtotal 42.00, descuento 0.00 y aplicador/fecha nulos; evento `LINE_DISCOUNT_REMOVED`.
10. **Valor 25:** HTTP 422, `INVALID_DISCOUNT_PERCENTAGE`; línea y total sin cambios.
11. **Ticket PAID:** HTTP 422, `TICKET_ALREADY_PAID`; estado, número comercial y total sin cambios.
12. **Eventos:** escrituras verificadas directamente para `TICKET_CREATED`, `TICKET_LINES_ADDED`, `LINE_DISCOUNT_APPLIED`, `LINE_DISCOUNT_REMOVED`, `TICKET_PAID` y `COMMERCIAL_NUMBER_ASSIGNED`; tenant 1, usuario 1, entityId, occurredAt e idempotencyKey correctos. No se detectaron credenciales en los JSON. La consulta HTTP devuelve 200 y permanece tenant-scoped.
13. **Conteo del smoke por acción:** `TICKET_CREATED=3`, `TICKET_LINES_ADDED=3`, `TICKET_PAID=3`, `COMMERCIAL_NUMBER_ASSIGNED=3`, `LINE_DISCOUNT_REMOVED=1`, `LINE_DISCOUNT_APPLIED=3` (1 éxito y 2 rechazos auditados). Los replays no añadieron eventos.
14. **Compile:** `mvnw.cmd clean compile` — BUILD SUCCESS.
15. **Test:** `mvnw.cmd test` — BUILD SUCCESS; 1 ejecutada, 0 fallos, 0 errores, 0 omitidas.
16. **Verify:** `mvnw.cmd clean verify` — BUILD SUCCESS; 1 ejecutada, 0 fallos, 0 errores, 0 omitidas.
17. **Mesas QA finales:** IDs 3, 4 y 5 FREE; ID 6 permaneció FREE. Ningún ticket QA quedó OPEN.
18. **Puerto final:** 8080 libre; se detuvo únicamente el proceso iniciado para esta auditoría.
19. **Limpieza:** sin `.log`, `.err`, `.pid`, `.tmp`, `.bak`, ZIP, `.verify-backend.pid` ni `target`. `.gitignore` contiene todos los patrones requeridos.
20. **Riesgos reales pendientes:** ninguno bloqueante identificado en el alcance de Fase 5.
21. **Decisión:** **GO**.

## Corrección final de auditoría

- Se eliminó la consulta JPQL que causaba SQLState 42P18 y se sustituyó por `JpaSpecificationExecutor` con predicados dinámicos. Los filtros `from` y `to` solo se incorporan cuando tienen valor; se mantienen tenant, action, entityType, entityId, userId y success, con orden descendente por occurredAt.
- `GET /api/admin/audit-events?page=0&size=100`: HTTP 200, 33 elementos y únicamente tenant 1.
- Sin `from`/`to`: HTTP 200. Solo `from`: HTTP 200. Solo `to`: HTTP 200. Ambos: HTTP 200.
- `size=101`: HTTP 400, `INVALID_PAGE_SIZE`. `page=-1`: HTTP 400, `INVALID_PAGE`.
- Seguridad conservada: el endpoint exige rol ADMIN o SUPER_ADMIN; CAMARERO queda excluido. El tenant se obtiene del usuario autenticado y es el primer predicado obligatorio de toda consulta.
- Flyway validó las 3 migraciones, mantuvo la versión 5.2 y no ejecutó ninguna nueva. Hibernate `validate` permitió el arranque correcto.
- Verificación final: `clean compile`, `test` y `clean verify` terminaron en BUILD SUCCESS.
