package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Asignación de un único acceso usuario-tienda (Fase 9, F9.5).
 */
@Getter
@Setter
public class AssignStoreRequestDto {

    @NotNull(message = "La tienda es obligatoria")
    private Long storeId;

    /** Si es true, la Store asignada pasa a ser la predeterminada. */
    private Boolean makeDefault;
}
