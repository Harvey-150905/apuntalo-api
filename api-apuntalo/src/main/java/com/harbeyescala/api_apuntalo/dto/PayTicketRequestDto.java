package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public class PayTicketRequestDto {

    @NotNull(message = "El método de pago es obligatorio")
    private PaymentMethod paymentMethod;

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}