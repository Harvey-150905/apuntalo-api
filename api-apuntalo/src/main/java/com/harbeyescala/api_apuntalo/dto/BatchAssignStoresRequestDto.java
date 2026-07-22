package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Asignación por lote de accesos usuario-tienda (Fase 9, F9.5). Define el
 * conjunto exacto de Stores activas y la Store predeterminada, que debe
 * pertenecer al conjunto. Las Stores fuera del conjunto se desactivan; las
 * inactivas existentes se reactivan en vez de duplicarse.
 */
@Getter
@Setter
public class BatchAssignStoresRequestDto {

    @NotEmpty(message = "Debe indicar al menos una tienda activa")
    private List<Long> activeStoreIds;

    @NotNull(message = "La tienda predeterminada es obligatoria")
    private Long defaultStoreId;
}
