package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserUpdateDto {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El username es obligatorio")
    private String username;

    @NotNull(message = "El rol es obligatorio")
    private Role role;

    @NotNull(message = "El negocioId es obligatorio")
    private Long negocioId;
}