package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Detalle administrativo de un usuario (Fase 9, F9.4), con su Store
 * predeterminada y el resumen de accesos (activos e inactivos).
 */
@Getter
@Builder
public class AdminUserDetailResponseDto {
    private Long id;
    private String nombre;
    private String username;
    private Role role;
    private Long negocioId;
    private String negocioNombre;
    private Boolean activo;
    private Long defaultStoreId;
    private StoreResponseDto defaultStore;
    private long activeStoreCount;
    private List<UserStoreAccessResponseDto> stores;
}
