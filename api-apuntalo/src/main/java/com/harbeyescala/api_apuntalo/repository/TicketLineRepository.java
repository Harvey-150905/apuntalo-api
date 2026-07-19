package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.dto.ProductSalesSummaryDto;
import com.harbeyescala.api_apuntalo.entity.TicketLine;
import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TicketLineRepository extends JpaRepository<TicketLine, Long> {

    List<TicketLine> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<TicketLine> findByTicketIdAndStatusOrderByCreatedAtAsc(Long ticketId, TicketLineStatus status);

    Optional<TicketLine> findTopByTicketIdOrderByBatchNumberDesc(Long ticketId);

    @Query("""
        SELECT new com.harbeyescala.api_apuntalo.dto.ProductSalesSummaryDto(
            tl.productId,
            tl.productNameSnapshot,
            SUM(tl.quantity),
            COALESCE(SUM(tl.subtotal), 0)
        )
        FROM TicketLine tl
        JOIN tl.ticket t
        WHERE t.negocio.id = :negocioId
          AND t.status = :ticketStatus
          AND tl.status = :lineStatus
          AND t.paidAt >= :from AND t.paidAt < :to
        GROUP BY tl.productId, tl.productNameSnapshot
        ORDER BY COALESCE(SUM(tl.subtotal), 0) DESC
    """)
    List<ProductSalesSummaryDto> findProductSalesSummary(
        @Param("negocioId") Long negocioId,
        @Param("ticketStatus") TicketStatus ticketStatus,
        @Param("lineStatus") TicketLineStatus lineStatus,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    List<TicketLine> findByTicketIdAndBatchNumberAndTicketNegocioId(
        Long ticketId,
        Integer batchNumber,
        Long negocioId
    );

    List<TicketLine> findByTicketIdAndTicketNegocioIdOrderByCreatedAtAsc(
        Long ticketId,
        Long negocioId
    );

    List<TicketLine> findByTicketIdAndBatchNumberOrderByCreatedAtAsc(Long ticketId, Integer batchNumber);

    Optional<TicketLine> findByIdAndTicketIdAndTicketNegocioId(Long id, Long ticketId, Long negocioId);
}
