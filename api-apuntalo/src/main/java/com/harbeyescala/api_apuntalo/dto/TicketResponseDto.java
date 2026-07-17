package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
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
public class TicketResponseDto {

    private Long id;
    private TicketStatus status;
    private BigDecimal total;
    private Long commercialNumber;
    private String commercialNumberFormatted;
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
}