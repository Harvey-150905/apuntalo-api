package com.harbeyescala.api_apuntalo.dto;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
@Setter
public class CashRegisterNameRequestDto {
    @NotBlank(message = "El nombre de la caja es obligatorio")
    @Size(max = 100, message = "El nombre de la caja no puede superar los 100 caracteres")
    private String name;
}
