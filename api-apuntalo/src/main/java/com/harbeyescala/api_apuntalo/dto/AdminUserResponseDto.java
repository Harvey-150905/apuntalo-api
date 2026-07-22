package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import lombok.Builder;
import lombok.Getter;

/**
 * Elemento de listado administrativo de usuarios (Fase 9, F9.4).
 */
@Getter
@Builder
public class AdminUserResponseDto {
    private Long id;
    private String nombre;
    private String username;
    private Role role;
    private Long negocioId;
    private Boolean activo;
    private Long defaultStoreId;
    private long activeStoreCount;
}
