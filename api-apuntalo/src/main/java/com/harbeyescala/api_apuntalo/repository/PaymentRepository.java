package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Payment;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @EntityGraph(attributePaths = {"paidBy", "cashSession", "cashSession.cashRegister", "cashSession.openedBy"})
    List<Payment> findByTicketIdAndNegocioIdOrderByIdAsc(Long ticketId, Long negocioId);

    @EntityGraph(attributePaths = {"cashSession", "cashSession.cashRegister", "cashSession.openedBy"})
    Optional<Payment> findFirstByTicketIdAndNegocioIdOrderByIdAsc(Long ticketId, Long negocioId);

    @EntityGraph(attributePaths = {"ticket", "cashSession", "cashSession.cashRegister", "cashSession.openedBy"})
    List<Payment> findByTicketIdInAndNegocioIdOrderByIdAsc(List<Long> ticketIds, Long negocioId);

    @Query("""
        SELECT COALESCE(SUM(p.amount), 0) FROM Payment p
        WHERE p.negocioId = :tenantId AND p.store.id = :storeId AND p.ticket.status = :status
          AND p.method = :method AND p.ticket.paidAt >= :from AND p.ticket.paidAt < :to
    """)
    BigDecimal sumAmount(@Param("tenantId") Long tenantId,
                         @Param("storeId") Long storeId,
                         @Param("status") TicketStatus status,
                         @Param("method") PaymentMethod method,
                         @Param("from") LocalDateTime from,
                         @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(p.amount),0) from Payment p where p.negocioId=:tenantId and p.store.id=:storeId and p.cashSession.id=:sessionId and p.method=:method")
    BigDecimal sumAmountBySession(@Param("tenantId") Long tenantId,@Param("storeId")Long storeId, @Param("sessionId") Long sessionId,
                                  @Param("method") PaymentMethod method);
}
