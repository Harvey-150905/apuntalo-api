package com.harbeyescala.api_apuntalo.entity;

import com.harbeyescala.api_apuntalo.entity.enums.CashMovementType;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name="cash_movements", indexes=@Index(name="idx_cash_movements_session_performed", columnList="negocio_id, cash_session_id, performed_at, id"))
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashMovement {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="negocio_id", nullable=false) private Negocio negocio;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="store_id", nullable=false) private Store store;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="cash_session_id", nullable=false) private CashSession cashSession;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private CashMovementType type;
    @Column(nullable=false, precision=10, scale=2) private BigDecimal amount;
    @Column(nullable=false, length=300) private String reason;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="performed_by", nullable=false) private User performedBy;
    @Column(name="performed_at", nullable=false) private LocalDateTime performedAt;
    @Column(name="created_at", nullable=false, updatable=false) private LocalDateTime createdAt;
}
