package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CashManagementConfigRequestDto {
    @NotNull(message = "El estado de reconciliación de caja es obligatorio")
    private Boolean enabled;
}
