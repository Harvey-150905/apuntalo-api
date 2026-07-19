package com.harbeyescala.api_apuntalo.entity;

import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionCloseMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_sessions", indexes =
        @Index(name = "idx_cash_sessions_negocio_store_status_opened", columnList = "negocio_id, store_id, status, opened_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "negocio_id", nullable = false)
    private Negocio negocio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cash_register_id", nullable = false)
    private CashRegister cashRegister;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashSessionStatus status;

    @Column(name = "opening_float", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingFloat;

    @Column(name = "reconciliation_required", nullable = false)
    private Boolean reconciliationRequired;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "opened_by", nullable = false)
    private User openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="closed_by") private User closedBy;
    @Column(name="closed_at") private LocalDateTime closedAt;
    @Enumerated(EnumType.STRING) @Column(name="close_mode", length=20) private CashSessionCloseMode closeMode;
    @Column(name="expected_cash_at_close", precision=10, scale=2) private BigDecimal expectedCashAtClose;
    @Column(name="counted_cash", precision=10, scale=2) private BigDecimal countedCash;
    @Column(precision=10, scale=2) private BigDecimal difference;
    @Column(name="pending_ticket_count_at_close") private Long pendingTicketCountAtClose;
    @Column(name="pending_ticket_amount_at_close", precision=10, scale=2) private BigDecimal pendingTicketAmountAtClose;
    @Column(name="pending_tickets_acknowledged") private Boolean pendingTicketsAcknowledged;
}
