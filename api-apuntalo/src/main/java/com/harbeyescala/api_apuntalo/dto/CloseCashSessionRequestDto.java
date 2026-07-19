package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CloseCashSessionRequestDto {
    @PositiveOrZero(message = "El efectivo contado no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El efectivo contado debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal countedCash;
    private Boolean acknowledgePendingTickets;
}
