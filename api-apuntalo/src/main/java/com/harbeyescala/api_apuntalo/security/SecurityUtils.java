package com.harbeyescala.api_apuntalo.security;

import com.harbeyescala.api_apuntalo.exception.UnauthorizedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilidad estática de acceso rápido a la identidad autenticada.
 * Mantenida por compatibilidad con el código existente; delega en
 * {@link AuthenticatedUserPrincipal} obtenido del SecurityContext de
 * Spring (nunca de las claims del JWT ni de un ThreadLocal propio).
 * Para código nuevo, preferir inyectar {@link CurrentUser}.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    private static AuthenticatedUserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Usuario no autenticado");
        }

        if (!(auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal)) {
            throw new UnauthorizedException("No se pudo obtener el contexto de seguridad");
        }

        return principal;
    }

    public static Long getUserId() {
        return getPrincipal().userId();
    }

    /**
     * @deprecated usar {@link #getTenantId()}. Se mantiene por compatibilidad
     * con el código existente que aún nombra el tenant como "negocio".
     */
    @Deprecated
    public static Long getNegocioId() {
        return getTenantId();
    }

    public static Long getTenantId() {
        return getPrincipal().tenantId();
    }

    public static String getRole() {
        return getPrincipal().role().name();
    }

    public static String getUsername() {
        return getPrincipal().username();
    }

    public static String getNegocioNombre() {
        return getPrincipal().tenantName();
    }

    public static boolean isSuperAdmin() {
        return getPrincipal().isSuperAdmin();
    }
}
