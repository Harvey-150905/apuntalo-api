package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.enums.CashMovementType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashMovementRequestDto {
    @NotNull(message = "El tipo de movimiento es obligatorio")
    private CashMovementType type;
    @NotNull(message = "El importe es obligatorio")
    @Positive(message = "El importe debe ser mayor que cero")
    @Digits(integer = 8, fraction = 2, message = "El importe debe tener como máximo 8 enteros y 2 decimales")
    private BigDecimal amount;
    @NotBlank(message = "El motivo es obligatorio")
    @Size(max = 300, message = "El motivo no puede superar los 300 caracteres")
    private String reason;
}
