package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.LoginRequestDto;
import com.harbeyescala.api_apuntalo.dto.LoginResponseDto;
import com.harbeyescala.api_apuntalo.dto.StoreResponseDto;
import com.harbeyescala.api_apuntalo.dto.SwitchStoreResponseDto;
import com.harbeyescala.api_apuntalo.dto.UserStoreAccessResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.UnauthorizedException;
import com.harbeyescala.api_apuntalo.repository.CashSessionRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Credenciales inválidas";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserStoreAccessService accessService;
    private final StoreRepository storeRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CurrentUser currentUser;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserStoreAccessService accessService,
                       StoreRepository storeRepository,
                       CashSessionRepository cashSessionRepository,
                       CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessService = accessService;
        this.storeRepository = storeRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.currentUser = currentUser;
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
        Store defaultStore = user.getDefaultStore();
        Store activeStore = defaultStore == null ? null
                : accessService.findValidActiveStore(user.getId(), defaultStore.getId(), tenantId);
        if (activeStore == null) {
            throw new UnauthorizedException(
                    "ACTIVE_STORE_NOT_AVAILABLE", "La tienda activa no está disponible");
        }

        String token = jwtService.generateToken(
                user, tenantId, activeStore.getId(), user.getTokenVersion());

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
                .activeStore(toStoreResponse(activeStore))
                .build();
    }

    /**
     * Fase 9 (F9.8): el responsable de una sesión de caja OPEN no puede
     * cambiar de Store hasta cerrarla (contrato F9 §7).
     */
    @Transactional(readOnly = true)
    public SwitchStoreResponseDto switchStore(Long requestedStoreId) {
        Long userId = currentUser.getUserId();
        Long tenantId = currentUser.getTenantId();

        if (cashSessionRepository.existsByOpenedByIdAndNegocioIdAndStatus(
                userId, tenantId, CashSessionStatus.OPEN)) {
            throw new ConflictException("OPEN_CASH_SESSION_PREVENTS_STORE_SWITCH",
                    "No puedes cambiar de tienda mientras tengas una sesión de caja abierta");
        }

        Store store = accessService.resolveStoreForSwitch(userId, requestedStoreId, tenantId);
        User user = userRepository.findByIdAndNegocioId(userId, tenantId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Token inválido o expirado"));
        String token = jwtService.generateToken(
                user, tenantId, store.getId(), user.getTokenVersion());
        return SwitchStoreResponseDto.builder()
                .token(token)
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpirationSeconds())
                .activeStore(toStoreResponse(store))
                .build();
    }

    @Transactional(readOnly = true)
    public java.util.List<UserStoreAccessResponseDto> authorizedStores() {
        return accessService.listAuthorizedActiveStores(
                currentUser.getUserId(), currentUser.getTenantId(), currentUser.requireCurrentStoreId());
    }

    @Transactional(readOnly = true)
    public StoreResponseDto currentActiveStore() {
        Store store = storeRepository.findByIdAndNegocioId(
                        currentUser.requireCurrentStoreId(), currentUser.getTenantId())
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Token inválido o expirado"));
        return toStoreResponse(store);
    }

    @Transactional(readOnly = true)
    public Long currentDefaultStoreId() {
        return userRepository.findWithDefaultStoreByIdAndNegocioId(
                        currentUser.getUserId(), currentUser.getTenantId())
                .map(User::getDefaultStore).map(Store::getId)
                .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Token inválido o expirado"));
    }

    private StoreResponseDto toStoreResponse(Store store) {
        return StoreResponseDto.builder()
                .id(store.getId()).name(store.getName()).code(store.getCode())
                .timezone(store.getTimezone()).active(store.getActive())
                .primaryStore(store.getPrimaryStore()).address(store.getAddress())
                .city(store.getCity()).countryCode(store.getCountryCode())
                .cashReconciliationEnabled(store.getCashReconciliationEnabled()).build();
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }

    private UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", INVALID_CREDENTIALS_MESSAGE);
    }
}
