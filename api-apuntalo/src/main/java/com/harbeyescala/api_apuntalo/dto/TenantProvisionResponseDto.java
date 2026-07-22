package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import lombok.Builder;
import lombok.Getter;

/**
 * Respuesta del provisionamiento de negocio (Fase 9, F9.2). Nunca devuelve
 * la contraseña del administrador creado.
 */
@Getter
@Builder
public class TenantProvisionResponseDto {

    private Long negocioId;
    private String negocioNombre;
    private StoreResponseDto principalStore;
    private AdminSummary admin;

    @Getter
    @Builder
    public static class AdminSummary {
        private Long id;
        private String nombre;
        private String username;
        private Role role;
        private Long defaultStoreId;
    }
}
