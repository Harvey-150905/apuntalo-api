package com.harbeyescala.api_apuntalo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Alta administrativa de una Store (Fase 9, F9.3). La Store se crea siempre
 * como no-Principal y activa. El {@code negocioId} solo lo usa un SUPER_ADMIN
 * para crear en otro tenant; un ADMIN lo ignora (usa su tenant del JWT).
 */
@Getter
@Setter
public class StoreCreateRequestDto {

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

    @Positive(message = "El negocioId debe ser un id válido")
    private Long negocioId;
}
