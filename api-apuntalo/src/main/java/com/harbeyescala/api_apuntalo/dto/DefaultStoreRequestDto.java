package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Cambio de la Store predeterminada de un usuario (Fase 9, F9.5). Debe
 * pertenecer al conjunto de accesos activos del usuario.
 */
@Getter
@Setter
public class DefaultStoreRequestDto {

    @NotNull(message = "La tienda predeterminada es obligatoria")
    private Long defaultStoreId;
}
