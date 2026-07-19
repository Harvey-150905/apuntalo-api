package com.harbeyescala.api_apuntalo.config;

import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.security.AuthenticatedUserPrincipal;
import com.harbeyescala.api_apuntalo.security.TokenPrincipalResolver;
import com.harbeyescala.api_apuntalo.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Valida el JWT en cada petición y reconstruye la identidad autenticada
 * siempre contra el estado actual en base de datos (Fase 1.6): un token
 * técnicamente válido pero perteneciente a un usuario desactivado, movido
 * de negocio, con rol cambiado o con password/rol/negocio rotado
 * (tokenVersion desincronizado) se rechaza igualmente.
 *
 * La validación contra BD se delega a {@link TokenPrincipalResolver}
 * (bean transaccional aparte): este filtro corre antes que el
 * DispatcherServlet, fuera del alcance de spring.jpa.open-in-view, así que
 * cualquier acceso a relaciones lazy debe ocurrir dentro de una
 * transacción explícita propia.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final TokenPrincipalResolver principalResolver;
    private final ApiErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, TokenPrincipalResolver principalResolver, ApiErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.principalResolver = principalResolver;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            AuthenticatedUserPrincipal principal = resolvePrincipal(token);

            List<GrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + principal.role().name())
            );

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            log.warn("JWT rechazado en {}: {}", request.getRequestURI(), e.getClass().getSimpleName());
            writeUnauthorized(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private AuthenticatedUserPrincipal resolvePrincipal(String token) {
        Claims claims = jwtService.extractClaims(token);

        Long userId = requiredPositiveLongClaim(claims, "userId");
        Long tenantId = requiredPositiveLongClaim(claims, "tenantId");
        Long storeId = requiredPositiveLongClaim(claims, "storeId");
        String roleClaim = claims.get("role", String.class);
        Integer tokenVersionClaim = claims.get("tokenVersion", Integer.class);
        String subject = claims.getSubject();

        if (userId == null || tenantId == null || roleClaim == null || tokenVersionClaim == null
                || subject == null || !subject.equals(String.valueOf(userId))) {
            throw new IllegalStateException("Token inválido: faltan claims obligatorias");
        }

        Role role;
        try {
            role = Role.valueOf(roleClaim);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Token inválido: rol desconocido");
        }

        return principalResolver.resolve(userId, tenantId, storeId, role, tokenVersionClaim);
    }

    private Long requiredPositiveLongClaim(Claims claims, String name) {
        Object raw = claims.get(name);
        if (!(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long)) {
            throw new IllegalStateException("Token inválido: claim numérica inválida");
        }
        long value = ((Number) raw).longValue();
        if (value <= 0) throw new IllegalStateException("Token inválido: claim numérica inválida");
        return value;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "INVALID_TOKEN", "Token inválido o expirado");
    }
}
