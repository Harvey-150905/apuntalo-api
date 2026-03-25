package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.dto.UserSalesSummaryDto;
import com.harbeyescala.api_apuntalo.entity.Ticket;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByNegocioId(Long negocioId);

    // List<Ticket> findByNegocioIdAndStatus(Long negocioId, TicketStatus status);

    List<Ticket> findByNegocioIdAndStatusOrderByPaidAtDesc(Long negocioId, TicketStatus status);

    List<Ticket> findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );
    @Query("""
        SELECT COALESCE(SUM(t.total), 0)
        FROM Ticket t
        WHERE t.negocio.id = :negocioId
        AND t.status = :status
        AND t.paidAt BETWEEN :from AND :to
    """)
    BigDecimal sumTotalByNegocioIdAndStatusAndPaidAtBetween(
        @Param("negocioId") Long negocioId,
        @Param("status") TicketStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    @Query("""
        SELECT COALESCE(SUM(t.total), 0)
        FROM Ticket t
        WHERE t.negocio.id = :negocioId
        AND t.status = :status
        AND t.paymentMethod = :paymentMethod
        AND t.paidAt BETWEEN :from AND :to
    """)
    BigDecimal sumTotalByNegocioIdAndStatusAndPaymentMethodAndPaidAtBetween(
        @Param("negocioId") Long negocioId,
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
        AND t.status = :status
        AND t.paidAt BETWEEN :from AND :to
        GROUP BY t.paidBy.id, t.paidBy.username
        ORDER BY COALESCE(SUM(t.total), 0) DESC
    """)
    List<UserSalesSummaryDto> findUserSalesSummaryByNegocioIdAndStatusAndPaidAtBetween(
        @Param("negocioId") Long negocioId,
        @Param("status") TicketStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
    List<Ticket> findByNegocioIdAndStatus(Long negocioId, TicketStatus status);

    List<Ticket> findByNegocioIdAndStatusOrderByCreatedAtDesc(Long negocioId, TicketStatus status);

    List<Ticket> findByNegocioIdAndStatusOrderByUpdatedAtDesc(Long negocioId, TicketStatus status);

    Long countByNegocioIdAndStatusAndPaidAtBetween(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );

    Long countByNegocioIdAndStatusAndUpdatedAtBetween(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to
    );

    Page<Ticket> findByNegocioIdAndStatusOrderByPaidAtDesc(
        Long negocioId,
        TicketStatus status,
        Pageable pageable
    );

    Page<Ticket> findByNegocioIdAndStatusAndPaidAtBetweenOrderByPaidAtDesc(
        Long negocioId,
        TicketStatus status,
        LocalDateTime from,
        LocalDateTime to,
        Pageable pageable
    );

    Page<Ticket> findByNegocioIdAndStatusOrderByCreatedAtDesc(
        Long negocioId,
        TicketStatus status,
        Pageable pageable
    );

    Page<Ticket> findByNegocioIdAndStatusOrderByUpdatedAtDesc(
        Long negocioId,
        TicketStatus status,
        Pageable pageable
    );

    Optional<Ticket> findByIdAndNegocioId(Long id, Long negocioId);

    Optional<Ticket> findByMesaIdAndNegocioIdAndStatus(Long mesaId, Long negocioId, TicketStatus status);

    boolean existsByMesaIdAndNegocioIdAndStatus(Long mesaId, Long negocioId, TicketStatus status);

    
}