package com.harbeyescala.api_apuntalo.dto;
import com.harbeyescala.api_apuntalo.entity.enums.CashMovementType;
import lombok.*;
import java.math.BigDecimal; import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CashMovementResponseDto { private Long movementId; private Long cashSessionId; private CashMovementType type; private BigDecimal amount; private String reason; private Long performedById; private String performedByUsername; private LocalDateTime performedAt; private BigDecimal expectedCashAfterMovement; }
