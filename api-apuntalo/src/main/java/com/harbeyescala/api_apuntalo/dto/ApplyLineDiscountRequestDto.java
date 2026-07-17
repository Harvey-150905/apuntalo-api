package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApplyLineDiscountRequestDto {

    /**
     * El catálogo exacto de porcentajes permitidos (0,5,10,15,20,30,35,40,
     * 45,50) se valida en {@code TicketLinePricingService}; aquí solo se
     * acota el rango para rechazar entradas obviamente inválidas pronto.
     */
    @NotNull(message = "El porcentaje de descuento es obligatorio")
    @Min(value = 0, message = "El porcentaje de descuento no puede ser negativo")
    @Max(value = 50, message = "El porcentaje de descuento no puede superar 50")
    private Integer discountPercentage;
}
