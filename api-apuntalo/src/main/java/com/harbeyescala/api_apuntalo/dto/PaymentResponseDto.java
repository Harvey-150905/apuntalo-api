package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponseDto {
    private Long paymentId;
    private PaymentMethod method;
    private BigDecimal amount;
    private BigDecimal cashReceived;
    private BigDecimal changeGiven;
    private LocalDateTime paidAt;
    private Long paidById;
    private String paidByUsername;
}
