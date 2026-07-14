package com.harbeyescala.api_apuntalo.security;

import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve y valida contra base de datos las claims de un JWT ya
 * verificado en firma/expiración (Fase 1.6). Se ejecuta en su propia
 * transacción de lectura porque {@link com.harbeyescala.api_apuntalo.config.JwtAuthenticationFilter}
 * corre antes que el DispatcherServlet, fuera del alcance de
 * spring.jpa.open-in-view: sin esta transacción explícita, acceder a
 * la relación lazy user.getNegocio() falla con LazyInitializationException.
 */
@Service
public class TokenPrincipalResolver {

    private final UserRepository userRepository;

    public TokenPrincipalResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal resolve(Long userId, Long tenantId, Role claimedRole, Integer claimedTokenVersion) {
        User user = userRepository.findByIdAndNegocioId(userId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Token inválido: el usuario ya no pertenece a ese tenant"));

        if (!Boolean.TRUE.equals(user.getActivo())) {
            throw new IllegalStateException("Usuario inactivo");
        }

        if (user.getNegocio() == null || !Boolean.TRUE.equals(user.getNegocio().getActivo())) {
            throw new IllegalStateException("Negocio inactivo");
        }

        if (user.getRole() != claimedRole) {
            throw new IllegalStateException("Token inválido: el rol ya no coincide");
        }

        if (!user.getTokenVersion().equals(claimedTokenVersion)) {
            throw new IllegalStateException("Token inválido: sesión invalidada");
        }

        return new AuthenticatedUserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getNegocio().getId(),
                user.getNegocio().getNombre(),
                user.getRole(),
                user.getTokenVersion()
        );
    }
}
