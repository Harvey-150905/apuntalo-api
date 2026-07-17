package com.harbeyescala.api_apuntalo.entity;

import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "ticket_lines",
    indexes = {
        @Index(name = "idx_ticketline_ticket", columnList = "ticket_id"),
        @Index(name = "idx_ticketline_ticket_status", columnList = "ticket_id, status"),
        @Index(name = "idx_ticketline_ticket_batch", columnList = "ticket_id, batch_number")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false)
    private String productNameSnapshot;

    @Column(name = "unit_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    /**
     * Descuentos por línea (Fase 5.1). {@code subtotalBeforeDiscount} es el
     * importe bruto (unitPrice * quantity); {@code subtotal} queda siempre
     * como {@code subtotalBeforeDiscount - discountAmount}. Sin descuento
     * (0%) ambos coinciden y el actor/fecha quedan nulos.
     */
    @Column(name = "subtotal_before_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotalBeforeDiscount;

    @Column(name = "discount_percentage", nullable = false)
    @Builder.Default
    private Integer discountPercentage = 0;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_applied_by")
    private User discountAppliedBy;

    @Column(name = "discount_applied_at")
    private LocalDateTime discountAppliedAt;

    @Column(name = "batch_number", nullable = false)
    private Integer batchNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TicketLineStatus status = TicketLineStatus.ACTIVE;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
