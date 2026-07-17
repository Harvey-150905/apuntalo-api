package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketLineResponseDto {

    private Long id;
    private Long productId;
    private String productNameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private Integer quantity;
    private BigDecimal subtotal;

    private BigDecimal subtotalBeforeDiscount;
    private Integer discountPercentage;
    private BigDecimal discountAmount;
    private Long discountAppliedById;
    private String discountAppliedByUsername;
    private LocalDateTime discountAppliedAt;

    private Integer batchNumber;
    private TicketLineStatus status;
    private String notes;
    private LocalDateTime createdAt;
}