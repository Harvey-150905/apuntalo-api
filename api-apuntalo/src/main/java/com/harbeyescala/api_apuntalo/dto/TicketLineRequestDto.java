package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TicketLineRequestDto {

    @NotNull(message = "El producto es obligatorio")
    private Long productId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor que 0")
    @Max(value = 100, message = "La cantidad no puede ser mayor que 100")
    private Integer quantity;

    @Size(max = 300, message = "Las notas no pueden superar los 300 caracteres")
    private String notes;
}