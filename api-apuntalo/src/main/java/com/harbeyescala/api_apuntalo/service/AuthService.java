package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.LoginRequestDto;
import com.harbeyescala.api_apuntalo.dto.LoginResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.exception.UnauthorizedException;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Flujo de login (Fase 1.4). Todas las causas de rechazo (usuario
     * inexistente, password incorrecta, usuario inactivo, negocio inactivo)
     * devuelven exactamente el mismo error genérico para no filtrar
     * información de seguridad.
     */
    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto dto) {
        String normalizedUsername = normalizeUsername(dto.getUsername());

        User user = userRepository.findByUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw invalidCredentials();
        }

        if (!Boolean.TRUE.equals(user.getActivo())) {
            throw invalidCredentials();
        }

        Negocio negocio = user.getNegocio();
        if (negocio == null || !Boolean.TRUE.equals(negocio.getActivo())) {
            throw invalidCredentials();
        }

        Long tenantId = negocio.getId();
        String tenantName = negocio.getNombre();

        String token = jwtService.generateToken(user);

        return LoginResponseDto.builder()
                .token(token)
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .user(LoginResponseDto.UserSummary.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .role(user.getRole())
                        .build())
                .tenant(LoginResponseDto.TenantSummary.builder()
                        .id(tenantId)
                        .name(tenantName)
                        .build())
                .build();
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", INVALID_CREDENTIALS_MESSAGE);
    }
}
