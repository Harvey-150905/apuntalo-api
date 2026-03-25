package com.harbeyescala.api_apuntalo.security;

import com.harbeyescala.api_apuntalo.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {
    }

    public static Claims getClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Usuario no autenticado");
        }

        Object details = auth.getDetails();
        if (!(details instanceof Claims claims)) {
            throw new UnauthorizedException("No se pudo obtener el contexto de seguridad");
        }

        return claims;
    }

    public static Long getUserId() {
        Long userId = getClaims().get("userId", Long.class);
        if (userId == null) {
            throw new UnauthorizedException("Token inválido: userId no encontrado");
        }
        return userId;
    }

    public static Long getNegocioId() {
        Long negocioId = getClaims().get("negocioId", Long.class);
        if (negocioId == null) {
            throw new UnauthorizedException("Token inválido: negocioId no encontrado");
        }
        return negocioId;
    }

    public static String getRole() {
        String role = getClaims().get("role", String.class);
        if (role == null || role.isBlank()) {
            throw new UnauthorizedException("Token inválido: role no encontrado");
        }
        return role;
    }
    public static String getUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            throw new RuntimeException("No se pudo obtener el contexto de seguridad");
        }

        return authentication.getName();
    }
    public static String getNegocioNombre() {
        String negocioNombre = getClaims().get("negocioNombre", String.class);
        if (negocioNombre == null || negocioNombre.isBlank()) {
            throw new UnauthorizedException("Token inválido: negocioNombre no encontrado");
        }
        return negocioNombre;
    }
}