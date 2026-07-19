package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CashRegisterStatusRequestDto {
    @NotNull(message = "El estado de la caja es obligatorio")
    private Boolean active;
}
