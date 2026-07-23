# F9 — Corrección de aislamiento Store en historial y reportes

Fecha: 2026-07-23  
Rama: `main`  
Alcance: exclusivamente hallazgos CRITICAL/HIGH de Store scope indicados en `AUDITORIA_ESTADO_FASE_9.md`.

## 1. Causa raíz

La migración multi-Store ya había añadido `tickets.store_id`, constraints e índices, pero varios métodos de `TicketService` siguieron invocando métodos heredados de repositorio cuyo único scope era `negocioId`.

El defecto no estaba en el principal ni en `ActiveStoreContext`: ambos resolvían correctamente tenant y Store activa. El `storeId` se perdía al construir la consulta.

Consecuencias:

- páginas PAID/OPEN/CANCELLED podían mezclar tickets de otras Stores del mismo tenant;
- reportes por usuario, producto y día eran tenant-wide;
- average-ticket dividía total Store-scoped entre count tenant-wide;
- cash-closing mezclaba importes Store-scoped con counts tenant-wide.

## 2. Endpoints corregidos

Sin cambios de ruta, request, response, roles, paginación ni códigos HTTP:

- `GET /api/tickets/paid`
- `GET /api/tickets/open`
- `GET /api/tickets/cancelled`
- `GET /api/tickets/paid/user-summary`
- `GET /api/tickets/paid/product-summary`
- `GET /api/tickets/paid/daily-summary`
- `GET /api/tickets/paid/average-ticket`
- `GET /api/tickets/cash-closing`

`/paid/total` y `/paid/payment-summary` ya eran Store-scoped y no se modificaron.

## 3. Servicios afectados

Métodos de `TicketService` corregidos:

1. `findPaidTicketsPaged`
2. `findOpenTicketsPaged`
3. `findCancelledTicketsPaged`
4. `getUserSalesSummary`
5. `getProductSalesSummary`
6. `getDailySalesSummary`
7. `getAverageTicketSummary`
8. `getCashClosingSummary`

Todos obtienen:

- `tenantId` desde `CurrentUser`;
- `storeId` desde `ActiveStoreContext`;
- estado y rango existentes.

No se añadió `tenantId` ni `storeId` a ningún request.

## 4. Queries anteriores

Usos defectuosos:

- páginas con `findByNegocioIdAndStatus...`;
- user summary con `WHERE t.negocio.id = :negocioId`;
- product summary con ticket limitado solo por negocio;
- daily input con lista tenant-wide;
- counts con `countByNegocioIdAndStatus...`.

Los totales monetarios y `PaymentRepository.sumAmount` ya incluían Store, produciendo la mezcla de scopes en average/cash-closing.

## 5. Queries corregidas

### TicketRepository

Nuevos métodos:

- `findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(..., Pageable)`
- `findByNegocioIdAndStoreIdAndStatusOrderByCreatedAtDesc(..., Pageable)`
- `findByNegocioIdAndStoreIdAndStatusOrderByUpdatedAtDesc(..., Pageable)`
- `findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(...)`
- `countByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(...)`
- `countByNegocioIdAndStoreIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(...)`

Query modificada:

- `findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(...)` conserva el nombre/DTO, incorpora parámetro `storeId` y condición `t.store.id = :storeId`.

### TicketLineRepository

Query modificada:

- `findProductSalesSummary(...)` incorpora `storeId` y filtra por `t.store.id = :storeId`.

El scope se deriva del ticket histórico, no del producto actual. Se conservan `productId`, `productNameSnapshot`, cantidades y subtotales históricos.

### Orden

- PAID: `paidAt DESC, id DESC`;
- OPEN: `createdAt DESC, id DESC`;
- CANCELLED: `updatedAt DESC, id DESC`.

La paginación continúa realizándose en BD mediante `Page`, nunca en memoria.

## 6. Garantía tenant + Store

Cada consulta corregida contiene simultáneamente:

```text
negocio_id = tenant autenticado
store_id   = Store activa
status     = estado requerido
fecha      >= from
fecha      < toExclusive
```

Los reportes sin rango de fecha conservan sus filtros existentes de estado y Store.

Los índices existentes en V8.4 cubren la forma principal:

- `idx_tickets_tenant_store_status`
- `idx_tickets_tenant_store_paid`
- `idx_payments_tenant_store_paid`

No fue necesaria una migración.

## 7. Revisión de usos tenant-wide

Clasificación de usos relevantes no modificados:

- `NegocioService.existsByNegocioIdAndStatus`: legítimo tenant-global para impedir desactivar un negocio con tickets abiertos en cualquier Store.
- `StoreAdminService.existsByNegocioIdAndStoreIdAndStatus`: administrativo Store-scoped.
- métodos operativos de `TicketService` con ticket/mesa: Store-scoped.
- carga batch de pagos por `ticketIds + tenantId`: segura porque los ids provienen de una página previamente Store-scoped y los ids de ticket son globalmente únicos.
- carga de líneas por `ticketId + tenantId`: segura tras cargar/bloquear previamente el ticket con tenant+Store.
- variantes tenant-wide heredadas del repository: se conservaron porque el encargo prohíbe eliminar métodos necesarios para guardas/administración; los endpoints afectados ya no las usan.

No se corrigieron ámbitos MEDIUM/LOW.

## 8. Tests añadidos

Archivo:

`src/test/java/com/harbeyescala/api_apuntalo/repository/TicketStoreScopeRepositoryTest.java`

Se añadió H2 con scope `test` para ejecutar consultas JPA reales. No son mocks de repository.

La fixture crea:

- tenant 1 con Store A y Store B;
- tenant 2 con otra Store;
- usuarios, mesas y tickets PAID/OPEN/CANCELLED diferenciados;
- líneas con snapshots de producto;
- pagos CASH/CARD.

Tres tests cubren:

1. páginas PAID/OPEN/CANCELLED de Store A excluyen Store B;
2. Store B obtiene sus propios datos;
3. tenant 2 queda aislado;
4. user summary excluye otra Store;
5. product summary excluye otra Store usando el ticket;
6. daily input excluye otra Store;
7. total/count de average usan Store A y producen 15.00;
8. cash-closing usa total 30.00, CASH 10.00, CARD 20.00, 2 pagados y 1 cancelado;
9. los métodos públicos de `TicketService` devuelven los mismos resultados Store-scoped de extremo a extremo.

El SQL observado contiene `negocio_id=? AND store_id=?` en las consultas corregidas.

Limitación: H2 valida JPQL, derivación Spring Data, wiring de servicio y resultados. No sustituye una regresión PostgreSQL real con Flyway.

## 9. Resultados

| Verificación | Resultado |
|---|---|
| Suite específica | BUILD SUCCESS; 3 tests |
| `.\mvnw.cmd clean compile` | BUILD SUCCESS; 199 fuentes |
| `.\mvnw.cmd test` | BUILD SUCCESS; 21 tests, 0 fallos/errores/skip |
| `.\mvnw.cmd clean verify` | BUILD SUCCESS; 21 tests; JAR generado |

Warning preexistente: `AuthService` usa/sobrescribe una API deprecada.

## 10. Smoke y regresión

No ejecutados en esta sesión:

- no hay listener API en 8080;
- `psql` no está instalado/disponible;
- no existen variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`;
- no se proporcionaron credenciales.

No se arrancó backend ni se generaron logs en el repositorio.

La evidencia documental previa de 79 PASS F9 y 48/48 F8 no se presenta como revalidada después de este cambio.

## 11. Archivos modificados

- `pom.xml`
- `src/main/java/com/harbeyescala/api_apuntalo/repository/TicketRepository.java`
- `src/main/java/com/harbeyescala/api_apuntalo/repository/TicketLineRepository.java`
- `src/main/java/com/harbeyescala/api_apuntalo/service/TicketService.java`
- `src/test/java/com/harbeyescala/api_apuntalo/repository/TicketStoreScopeRepositoryTest.java`
- `F9_STORE_SCOPE_FIX_REPORT.md`

Se preservaron los informes previos no rastreados.

## 12. Migraciones

Ninguna creada ni modificada. V8.x y V9.1 permanecen intactas.

## 13. Riesgos restantes

- ejecutar la misma suite/regresión contra PostgreSQL real;
- repetir smoke F9 y regresión F8;
- el resumen diario sigue cargando la lista Store-scoped completa y agregando en memoria para rangos grandes;
- permanecen métodos tenant-wide heredados, aunque ya no se usan en estos flujos;
- warning deprecado de `AuthService`.

Fuera de alcance y expresamente pendiente:

- política timezone por Store;
- filtros nuevos de historial;
- optimizaciones/refactors grandes;
- Fase 10.

## 14. Veredicto del bloque Store scope

**GO para el bloque de aislamiento Store corregido**, respaldado por compile/test/verify y pruebas JPA multi-Store en H2.

El GO global de Fase 9 queda condicionado a:

- verificación PostgreSQL/Flyway;
- smoke F9;
- regresión F8;
- los demás pendientes de la auditoría que no formaban parte de este encargo.

No se implementó timezone, filtros nuevos ni Fase 10. No se hizo commit ni push.
