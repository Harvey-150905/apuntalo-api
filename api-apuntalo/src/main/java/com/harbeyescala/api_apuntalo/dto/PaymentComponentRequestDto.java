package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.PaymentMethod;

import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class PaymentComponentRequestDto {

    @NotNull(message = "El método de pago es obligatorio")
    private PaymentMethod method;
    @NotNull(message = "El importe del pago es obligatorio")
    @Positive(message = "El importe del pago debe ser mayor que cero")
    @Digits(integer = 8, fraction = 2, message = "El importe debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal amount;
    @PositiveOrZero(message = "El efectivo recibido no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El efectivo recibido debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal cashReceived;
    private BigDecimal changeGiven;

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getCashReceived() { return cashReceived; }
    public void setCashReceived(BigDecimal cashReceived) { this.cashReceived = cashReceived; }
    public BigDecimal getChangeGiven() { return changeGiven; }
    public void setChangeGiven(BigDecimal changeGiven) { this.changeGiven = changeGiven; }
}
