package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwitchStoreRequestDto {
    @NotNull(message = "La tienda es obligatoria")
    @Positive(message = "La tienda debe tener un id válido")
    private Long storeId;
}
