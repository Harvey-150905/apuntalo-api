package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class UserResponseDto {

    private Long id;
    private String nombre;
    private String username;
    private Role role;
    private Long negocioId;
    private String negocioNombre;
}