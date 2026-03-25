package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NegocioRequestDto {

    @NotBlank(message = "El nombre del negocio es obligatorio")
    private String nombre;
}