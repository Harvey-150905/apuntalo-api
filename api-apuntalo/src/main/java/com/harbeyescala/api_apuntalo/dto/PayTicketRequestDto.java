package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class PayTicketRequestDto {

    @NotNull(message = "La sesión de cobro es obligatoria")
    @Positive(message = "La sesión de cobro debe ser un id válido")
    private Long cashSessionId;

    @Deprecated
    private PaymentMethod paymentMethod;

    @Valid
    private List<@NotNull(message = "Los componentes de pago no pueden contener elementos nulos") @Valid PaymentComponentRequestDto> payments;

    public Long getCashSessionId() { return cashSessionId; }
    public void setCashSessionId(Long cashSessionId) { this.cashSessionId = cashSessionId; }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public List<PaymentComponentRequestDto> getPayments() {
        return payments;
    }

    public void setPayments(List<PaymentComponentRequestDto> payments) {
        this.payments = payments;
    }
}
