package com.harbeyescala.api_apuntalo.security;

import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Punto único de acceso a la identidad autenticada de la petición actual.
 * Se apoya en el {@link SecurityContextHolder} estándar de Spring
 * (sin ThreadLocal propio) que ya es gestionado por el filtro JWT.
 */
@Component
public class CurrentUser {

    public AuthenticatedUserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Usuario no autenticado");
        }

        if (!(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            throw new UnauthorizedException("No se pudo obtener el contexto de seguridad");
        }

        return principal;
    }

    public Long getUserId() {
        return getPrincipal().userId();
    }

    public Long getTenantId() {
        return getPrincipal().tenantId();
    }

    public String getUsername() {
        return getPrincipal().username();
    }

    public Role getRole() {
        return getPrincipal().role();
    }

    public boolean isSuperAdmin() {
        return getPrincipal().isSuperAdmin();
    }
}
