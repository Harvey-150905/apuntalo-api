package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TicketDetailResponseDto {

    private Long id;
    private TicketStatus status;
    private BigDecimal total;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;

    private Long mesaId;
    private Integer mesaNumero;

    private Long createdById;
    private String createdByUsername;

    private PaymentMethod paymentMethod;
    private Long paidById;
    private String paidByUsername;

    private List<TicketLineResponseDto> lines;
}