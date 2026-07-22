package com.harbeyescala.api_apuntalo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Reseteo administrativo de contraseña (Fase 9, F9.4). Mínimo 8 caracteres.
 * La contraseña nunca se devuelve ni se audita.
 */
@Getter
@Setter
public class ResetPasswordRequestDto {

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String newPassword;
}
