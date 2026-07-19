package com.harbeyescala.api_apuntalo.security;

import com.harbeyescala.api_apuntalo.entity.Role;

/**
 * Identidad autenticada resuelta y verificada contra base de datos por
 * {@link com.harbeyescala.api_apuntalo.config.JwtAuthenticationFilter}.
 * El resto del backend debe depender de este objeto, nunca leer claims
 * del JWT directamente.
 */
public record AuthenticatedUserPrincipal(
        Long userId,
        String username,
        Long tenantId,
        String tenantName,
        Long activeStoreId,
        Role role,
        Integer tokenVersion
) {

    public AuthenticatedUserPrincipal {
        if (activeStoreId == null) {
            throw new IllegalArgumentException("La tienda activa es obligatoria");
        }
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }
}
