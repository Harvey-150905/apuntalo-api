package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.CashSession;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import com.harbeyescala.api_apuntalo.repository.projection.CashSessionSummaryProjection;

public interface CashSessionRepository extends JpaRepository<CashSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashSession s where s.id = :id and s.negocio.id = :negocioId")
    Optional<CashSession> findByIdAndNegocioIdForUpdate(@Param("id") Long id,
                                                        @Param("negocioId") Long negocioId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashSession s where s.id=:id and s.negocio.id=:negocioId and s.store.id=:storeId")
    Optional<CashSession> findByIdAndNegocioIdAndStoreIdForUpdate(@Param("id")Long id,
      @Param("negocioId")Long negocioId,@Param("storeId")Long storeId);
    @EntityGraph(attributePaths = {"cashRegister", "openedBy"})
    Optional<CashSession> findByIdAndNegocioId(Long id, Long negocioId);
    @EntityGraph(attributePaths = {"cashRegister", "openedBy"})
    Optional<CashSession> findByIdAndNegocioIdAndStoreId(Long id,Long negocioId,Long storeId);
    boolean existsByIdAndNegocioIdAndStoreId(Long id,Long negocioId,Long storeId);
    List<CashSession> findByNegocioIdAndStoreIdAndStatusOrderByOpenedAtAscIdAsc(Long negocioId,Long storeId,CashSessionStatus status);
    Optional<CashSession> findByOpenedByIdAndNegocioIdAndStoreIdAndStatus(Long userId,Long negocioId,Long storeId,CashSessionStatus status);
    boolean existsByIdAndNegocioId(Long id, Long negocioId);

    Optional<CashSession> findByCashRegisterIdAndNegocioIdAndStatus(
            Long cashRegisterId, Long negocioId, CashSessionStatus status);
    boolean existsByCashRegisterIdAndNegocioIdAndStoreIdAndStatus(
            Long cashRegisterId, Long negocioId, Long storeId, CashSessionStatus status);

    @EntityGraph(attributePaths = {"cashRegister", "openedBy"})
    Optional<CashSession> findByOpenedByIdAndNegocioIdAndStatus(
            Long openedById, Long negocioId, CashSessionStatus status);
    boolean existsByOpenedByIdAndNegocioIdAndStatus(
            Long openedById, Long negocioId, CashSessionStatus status);

    @EntityGraph(attributePaths = {"cashRegister", "openedBy"})
    List<CashSession> findByNegocioIdAndStatusOrderByOpenedAtAscIdAsc(
            Long negocioId, CashSessionStatus status);
    boolean existsByNegocioIdAndStatus(Long negocioId, CashSessionStatus status);
    boolean existsByNegocioIdAndStoreIdAndStatus(Long negocioId,Long storeId,CashSessionStatus status);

    @Query(value = CashSessionRepository.SUMMARY_SQL + " WHERE s.negocio_id = :tenantId AND s.store_id = :storeId AND s.id = :sessionId " +
            " GROUP BY s.id, cr.id, cr.name, u.id, u.username, cu.id, cu.username", nativeQuery = true)
    Optional<CashSessionSummaryProjection> findSummary(@Param("tenantId") Long tenantId,
                                                       @Param("storeId") Long storeId,
                                                       @Param("sessionId") Long sessionId);

    @Query(value = CashSessionRepository.SUMMARY_SQL + " WHERE s.negocio_id = :tenantId AND s.store_id = :storeId AND s.status = 'OPEN' " +
            " GROUP BY s.id, cr.id, cr.name, u.id, u.username, cu.id, cu.username ORDER BY cr.name, s.id", nativeQuery = true)
    List<CashSessionSummaryProjection> findOpenSummaries(@Param("tenantId") Long tenantId,
                                                         @Param("storeId") Long storeId);

    String SUMMARY_SQL = """
        SELECT s.id AS "sessionId", s.status AS "status", cr.id AS "cashRegisterId", cr.name AS "cashRegisterName",
          u.id AS "responsibleUserId", u.username AS "responsibleUsername", s.opened_at AS "openedAt",
          s.opening_float AS "openingFloat", s.reconciliation_required AS "reconciliationRequired",
          COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'CASH'), 0) AS "cashSales",
          COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'CARD'), 0) AS "cardSales",
          COALESCE(SUM(p.amount), 0) AS "totalSales",
          (SELECT COALESCE(SUM(m.amount),0) FROM cash_movements m WHERE m.negocio_id=s.negocio_id AND m.cash_session_id=s.id AND m.type='CASH_IN') AS "cashIn",
          (SELECT COALESCE(SUM(m.amount),0) FROM cash_movements m WHERE m.negocio_id=s.negocio_id AND m.cash_session_id=s.id AND m.type='CASH_OUT') AS "cashOut",
          CASE WHEN s.status='CLOSED' THEN s.expected_cash_at_close ELSE
           s.opening_float + COALESCE(SUM(p.amount) FILTER (WHERE p.method = 'CASH'), 0)
           + (SELECT COALESCE(SUM(m.amount),0) FROM cash_movements m WHERE m.negocio_id=s.negocio_id AND m.cash_session_id=s.id AND m.type='CASH_IN')
           - (SELECT COALESCE(SUM(m.amount),0) FROM cash_movements m WHERE m.negocio_id=s.negocio_id AND m.cash_session_id=s.id AND m.type='CASH_OUT') END AS "expectedCash",
          s.closed_at AS "closedAt", cu.id AS "closedById", cu.username AS "closedByUsername",
          s.close_mode AS "closeMode", s.expected_cash_at_close AS "expectedCashAtClose",
          s.counted_cash AS "countedCash", s.difference AS "difference",
          s.pending_ticket_count_at_close AS "pendingTicketCountAtClose",
          s.pending_ticket_amount_at_close AS "pendingTicketAmountAtClose",
          s.pending_tickets_acknowledged AS "pendingTicketsAcknowledged",
          COUNT(DISTINCT p.ticket_id) AS "ticketCount",
          COUNT(p.id) FILTER (WHERE p.method = 'CASH') AS "cashPaymentCount",
          COUNT(p.id) FILTER (WHERE p.method = 'CARD') AS "cardPaymentCount",
          (SELECT COUNT(*) FROM tickets t WHERE t.negocio_id=s.negocio_id AND t.origin_cash_session_id=s.id)
            AS "ticketsOpenedCount",
          (SELECT COUNT(*) FROM tickets t WHERE t.negocio_id=s.negocio_id AND t.origin_cash_session_id=s.id
             AND t.status='OPEN') AS "openOriginTicketsCount",
          (SELECT COALESCE(SUM(t.total),0) FROM tickets t WHERE t.negocio_id=s.negocio_id
             AND t.origin_cash_session_id=s.id AND t.status='OPEN') AS "openOriginTicketsAmount",
          (SELECT COUNT(*) FROM tickets t WHERE t.negocio_id=s.negocio_id AND t.origin_cash_session_id=s.id
             AND t.status='PAID' AND EXISTS (SELECT 1 FROM payments px WHERE px.ticket_id=t.id
               AND px.negocio_id=s.negocio_id AND px.cash_session_id=s.id)) AS "ticketsOriginatedHerePaidHereCount",
          (SELECT COUNT(*) FROM tickets t WHERE t.negocio_id=s.negocio_id AND t.origin_cash_session_id=s.id
             AND t.status='PAID' AND EXISTS (SELECT 1 FROM payments px WHERE px.ticket_id=t.id
               AND px.negocio_id=s.negocio_id AND px.cash_session_id<>s.id)) AS "ticketsOriginatedHerePaidElsewhereCount",
          (SELECT COUNT(DISTINCT px.ticket_id) FROM payments px JOIN tickets t ON t.id=px.ticket_id
             AND t.negocio_id=px.negocio_id WHERE px.negocio_id=s.negocio_id AND px.cash_session_id=s.id
             AND t.origin_cash_session_id IS NOT NULL AND t.origin_cash_session_id<>s.id)
             AS "ticketsFromOtherSessionsPaidHereCount"
        FROM cash_sessions s
        JOIN cash_registers cr ON cr.id=s.cash_register_id AND cr.negocio_id=s.negocio_id
        JOIN users u ON u.id=s.opened_by AND u.negocio_id=s.negocio_id
        LEFT JOIN users cu ON cu.id=s.closed_by AND cu.negocio_id=s.negocio_id
        LEFT JOIN payments p ON p.cash_session_id=s.id AND p.negocio_id=s.negocio_id
        """;
}
