package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MesaRequestDto {

    @NotNull(message = "El número de mesa es obligatorio")
    @Min(value = 1, message = "El número de mesa debe ser mayor que 0")
    private Integer numero;

    private Boolean activa;
}