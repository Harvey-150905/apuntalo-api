package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Edición administrativa de un usuario (Fase 9, F9.4). No cambia el tenant ni
 * la contraseña (usar el endpoint de reset) ni el estado (usar PATCH status).
 */
@Getter
@Setter
public class AdminUserUpdateRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El username es obligatorio")
    @Size(max = 100, message = "El username no puede superar los 100 caracteres")
    private String username;

    @NotNull(message = "El rol es obligatorio")
    private Role role;
}
