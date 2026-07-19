package com.harbeyescala.api_apuntalo.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public class OpenCashSessionRequestDto {
    @NotNull(message = "La caja es obligatoria")
    @Positive(message = "La caja debe ser un id válido")
    private Long cashRegisterId;
    @NotNull(message = "El fondo inicial es obligatorio")
    @PositiveOrZero(message = "El fondo inicial no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El fondo inicial debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal openingFloat;

    public Long getCashRegisterId() { return cashRegisterId; }
    public void setCashRegisterId(Long cashRegisterId) { this.cashRegisterId = cashRegisterId; }
    public BigDecimal getOpeningFloat() { return openingFloat; }
    public void setOpeningFloat(BigDecimal openingFloat) { this.openingFloat = openingFloat; }
}
