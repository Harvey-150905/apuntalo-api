package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Edición administrativa de una Store (Fase 9, F9.3). No permite cambiar el
 * tenant ni la condición de Principal.
 */
@Getter
@Setter
public class StoreUpdateRequestDto {

    @NotBlank(message = "El nombre de la tienda es obligatorio")
    private String name;

    @NotBlank(message = "El código de la tienda es obligatorio")
    private String code;

    @NotBlank(message = "La zona horaria es obligatoria")
    private String timezone;

    @NotBlank(message = "El código de país es obligatorio")
    private String countryCode;

    private String address;
    private String city;
}
