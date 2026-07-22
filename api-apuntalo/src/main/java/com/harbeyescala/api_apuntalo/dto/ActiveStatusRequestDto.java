package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Cuerpo genérico para los endpoints administrativos {@code PATCH .../status}
 * de la Fase 9 (negocio, tienda, usuario, mesa, subcategoría, producto).
 */
@Getter
@Setter
public class ActiveStatusRequestDto {
    @NotNull(message = "El estado es obligatorio")
    private Boolean active;
}
