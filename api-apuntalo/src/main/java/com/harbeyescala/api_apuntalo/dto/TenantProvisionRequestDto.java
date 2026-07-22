package com.harbeyescala.api_apuntalo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Petición de provisionamiento atómico de un negocio (Fase 9, F9.2). Crea el
 * negocio, su Store Principal, la secuencia de numeración y el primer usuario
 * ADMIN con su acceso y Store predeterminada. Solo accesible por SUPER_ADMIN.
 */
@Getter
@Setter
public class TenantProvisionRequestDto {

    @NotBlank(message = "El nombre del negocio es obligatorio")
    private String negocioNombre;

    // --- Store Principal (opcional; valores por defecto sensatos) ---
    private String storeName;
    private String storeCode;
    private String storeTimezone;
    private String storeCountryCode;
    private String storeAddress;
    private String storeCity;

    // --- Primer administrador ---
    @NotBlank(message = "El nombre del administrador es obligatorio")
    private String adminNombre;

    @NotBlank(message = "El username del administrador es obligatorio")
    @Size(max = 100, message = "El username no puede superar los 100 caracteres")
    private String adminUsername;

    @NotBlank(message = "La contraseña del administrador es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String adminPassword;
}
