package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.dto.UserSalesSummaryDto;
import com.harbeyescala.api_apuntalo.entity.Ticket;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.harbeyescala.api_apuntalo.repository.projection.PendingCashSessionTicketProjection;
import com.harbeyescala.api_apuntalo.repository.projection.PendingTicketAggregateProjection;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByNegocioId(Long negocioId);

    // List<Ticket> findByNegocioIdAndStatus(Long negocioId, TicketStatus status);

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    List<Ticket> findByNegocioIdAndStatusOrderByPaidAtDesc(Long negocioId, TicketStatus status);
    List<Ticket> findByNegocioIdAndStoreIdAndStatusOrderByPaidAtDesc(Long negocioId,Long storeId,TicketStatus status);

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    List<Ticket> findByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );
    @Query("""
        SELECT COALESCE(SUM(t.total), 0)
        FROM Ticket t
        WHERE t.negocio.id = :negocioId
        AND t.store.id = :storeId
        AND t.status = :status
        AND t.paidAt >= :from AND t.paidAt < :to
    """)
    BigDecimal sumTotalByNegocioIdAndStatusAndPaidAtBetween(
        @Param("negocioId") Long negocioId,
        @Param("storeId") Long storeId,
        @Param("status") TicketStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COALESCE(SUM(t.total), 0)
        FROM Ticket t
        WHERE t.negocio.id = :negocioId
        AND t.store.id = :storeId
        AND t.status = :status
        AND t.paymentMethod = :paymentMethod
        AND t.paidAt >= :from AND t.paidAt < :to
    """)
    BigDecimal sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
        @Param("negocioId") Long negocioId,
        @Param("storeId") Long storeId,
        @Param("status") TicketStatus status,
        @Param("paymentMethod") PaymentMethod paymentMethod,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
    SELECT new com.harbeyescala.api_apuntalo.dto.UserSalesSummaryDto(
        t.paidBy.id,
        t.paidBy.username,
        COALESCE(SUM(t.total), 0)
        )
        FROM Ticket t
        WHERE t.negocio.id = :negocioId
        AND t.store.id = :storeId
        AND t.status = :status
        AND t.paidAt >= :from AND t.paidAt < :to
        GROUP BY t.paidBy.id, t.paidBy.username
        ORDER BY COALESCE(SUM(t.total), 0) DESC
    """)
    List<UserSalesSummaryDto> findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(
        @Param("negocioId") Long negocioId,
        @Param("storeId") Long storeId,
        @Param("status") TicketStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
    List<Ticket> findByNegocioIdAndStatus(Long negocioId, TicketStatus status);

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    List<Ticket> findByNegocioIdAndStatusOrderByCreatedAtDesc(Long negocioId, TicketStatus status);
    List<Ticket> findByNegocioIdAndStoreIdAndStatusOrderByCreatedAtDesc(Long negocioId,Long storeId,TicketStatus status);

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    List<Ticket> findByNegocioIdAndStatusOrderByUpdatedAtDesc(Long negocioId, TicketStatus status);
    List<Ticket> findByNegocioIdAndStoreIdAndStatusOrderByUpdatedAtDesc(Long negocioId,Long storeId,TicketStatus status);

    Long countByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );
    Long countByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThan(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );

    Long countByNegocioIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );
    Long countByNegocioIdAndStoreIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThan(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );

    Long countByNegocioIdAndStoreIdAndStatusAndCancelledAtGreaterThanEqualAndCancelledAtLessThan(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStatusOrderByPaidAtDesc(
        Long negocioId,
        TicketStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.negocio.id = :negocioId
          AND t.store.id = :storeId
          AND t.status = :status
          AND t.paidAt >= :from
          AND t.paidAt < :to
          AND (:paymentMethod IS NULL OR t.paymentMethod = :paymentMethod)
          AND (:userId IS NULL OR t.paidBy.id = :userId)
          AND (:mesaId IS NULL OR t.mesa.id = :mesaId)
          AND (:commercialNumber IS NULL OR t.commercialNumber = :commercialNumber)
        """)
    Page<Ticket> searchPaidHistory(
            @Param("negocioId") Long negocioId,
            @Param("storeId") Long storeId,
            @Param("status") TicketStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("paymentMethod") PaymentMethod paymentMethod,
            @Param("userId") Long userId,
            @Param("mesaId") Long mesaId,
            @Param("commercialNumber") Long commercialNumber,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStatusOrderByCreatedAtDesc(
        Long negocioId,
        TicketStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStoreIdAndStatusOrderByCreatedAtDesc(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStatusOrderByUpdatedAtDesc(
        Long negocioId,
        TicketStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Page<Ticket> findByNegocioIdAndStoreIdAndStatusOrderByUpdatedAtDesc(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    List<Ticket> findByNegocioIdAndStoreIdAndStatusAndPaidAtGreaterThanEqualAndPaidAtLessThanOrderByPaidAtDesc(
        Long negocioId,
        Long storeId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Optional<Ticket> findByIdAndNegocioId(Long id, Long negocioId);
    @EntityGraph(attributePaths = {"mesa","createdBy","paidBy","originCashSession"})
    Optional<Ticket> findByIdAndNegocioIdAndStoreId(Long id,Long negocioId,Long storeId);

    @EntityGraph(attributePaths = {"mesa", "createdBy", "paidBy", "originCashSession",
            "originCashSession.cashRegister", "originCashSession.openedBy"})
    Optional<Ticket> findByMesaIdAndNegocioIdAndStatus(Long mesaId, Long negocioId, TicketStatus status);
    Optional<Ticket> findByMesaIdAndNegocioIdAndStoreIdAndStatus(Long mesaId,Long negocioId,Long storeId,TicketStatus status);

    boolean existsByMesaIdAndNegocioIdAndStatus(Long mesaId, Long negocioId, TicketStatus status);
    boolean existsByMesaIdAndNegocioIdAndStoreIdAndStatus(Long mesaId,Long negocioId,Long storeId,TicketStatus status);

    // Fase 9: guardas de desactivación (negocio/Store) por tickets abiertos.
    boolean existsByNegocioIdAndStatus(Long negocioId, TicketStatus status);
    boolean existsByNegocioIdAndStoreIdAndStatus(Long negocioId, Long storeId, TicketStatus status);

    /**
     * Carga el ticket bajo bloqueo pesimista de escritura para cualquier
     * operación que vaya a leer/mutar su estado o total (Fase 3.3).
     * Serializa a nivel de fila las peticiones concurrentes sobre el mismo
     * ticket sin bloquear el resto de la tabla.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :ticketId and t.negocio.id = :tenantId")
    Optional<Ticket> findByIdAndNegocioIdForUpdate(
            @Param("ticketId") Long ticketId,
            @Param("tenantId") Long tenantId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id=:ticketId and t.negocio.id=:tenantId and t.store.id=:storeId")
    Optional<Ticket> findByIdAndNegocioIdAndStoreIdForUpdate(@Param("ticketId")Long ticketId,
      @Param("tenantId")Long tenantId,@Param("storeId")Long storeId);

    @Query(value = """
        SELECT t.id AS "ticketId", t.commercial_number AS "commercialNumber",
          m.id AS "mesaId", m.numero AS "mesaNumero", t.created_at AS "createdAt",
          u.id AS "createdById", u.username AS "createdByUsername", t.total AS "total",
          COUNT(tl.id) FILTER (WHERE tl.status='ACTIVE') AS "activeLineCount"
        FROM tickets t JOIN mesas m ON m.id=t.mesa_id
        JOIN users u ON u.id=t.created_by AND u.negocio_id=t.negocio_id
        LEFT JOIN ticket_lines tl ON tl.ticket_id=t.id
        WHERE t.negocio_id=:tenantId AND t.origin_cash_session_id=:sessionId AND t.status='OPEN'
        GROUP BY t.id, m.id, m.numero, u.id, u.username
        ORDER BY t.created_at, t.id
        """, nativeQuery = true)
    List<PendingCashSessionTicketProjection> findPendingByOriginSession(
            @Param("tenantId") Long tenantId, @Param("sessionId") Long sessionId);

    @Query("""
      select count(t) as ticketCount, coalesce(sum(t.total),0) as totalAmount from Ticket t
      where t.negocio.id=:tenantId and t.originCashSession.id=:sessionId and t.status=:status
      """)
    PendingTicketAggregateProjection aggregatePendingByOriginSession(
            @Param("tenantId") Long tenantId, @Param("sessionId") Long sessionId,
            @Param("status") TicketStatus status);
}
