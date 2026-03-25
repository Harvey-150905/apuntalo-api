package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.TicketLineStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TicketLineResponseDto {

    private Long id;
    private Long productId;
    private String productNameSnapshot;
    private BigDecimal unitPriceSnapshot;
    private Integer quantity;
    private BigDecimal subtotal;
    private Integer batchNumber;
    private TicketLineStatus status;
    private String notes;
    private LocalDateTime createdAt;
}