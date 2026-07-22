package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.StoreResponseDto;
import com.harbeyescala.api_apuntalo.dto.TenantProvisionRequestDto;
import com.harbeyescala.api_apuntalo.dto.TenantProvisionResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccessId;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.repository.TicketNumberSequenceRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.repository.UserStoreAccessRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Provisionamiento atómico de negocio (Fase 9, F9.2). En una única
 * transacción crea el Negocio, su Store Principal, la fila de secuencia de
 * numeración, el primer usuario ADMIN, su acceso a la Principal y su Store
 * predeterminada, más los eventos de auditoría con el tenant recién creado.
 *
 * <p>Cualquier fallo (p.ej. username duplicado) revierte todo. Solo debe
 * invocarlo un SUPER_ADMIN (protegido en {@code SecurityConfig}).
 */
@Service
public class TenantProvisioningService {

    private static final String DEFAULT_STORE_NAME = "Principal";
    private static final String DEFAULT_STORE_CODE = "PRINCIPAL";
    private static final String DEFAULT_TIMEZONE = "Europe/Madrid";
    private static final String DEFAULT_COUNTRY_CODE = "ES";

    private final NegocioRepository negocioRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final TicketNumberSequenceRepository sequenceRepository;
    private final UserRepository userRepository;
    private final UserStoreAccessRepository accessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEventService;
    private final Clock clock;

    public TenantProvisioningService(
            NegocioRepository negocioRepository,
            StoreRepository storeRepository,
            StoreService storeService,
            TicketNumberSequenceRepository sequenceRepository,
            UserRepository userRepository,
            UserStoreAccessRepository accessRepository,
            PasswordEncoder passwordEncoder,
            AuditEventService auditEventService,
            Clock clock) {
        this.negocioRepository = negocioRepository;
        this.storeRepository = storeRepository;
        this.storeService = storeService;
        this.sequenceRepository = sequenceRepository;
        this.userRepository = userRepository;
        this.accessRepository = accessRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
        this.clock = clock;
    }

    @Transactional
    public TenantProvisionResponseDto provision(TenantProvisionRequestDto dto) {
        String normalizedUsername = AuthService.normalizeUsername(dto.getAdminUsername());
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new ConflictException(
                    "USERNAME_ALREADY_EXISTS", "Ya existe un usuario con ese username");
        }

        // Normalización y validación de los datos de la Store Principal.
        String name = storeService.cleanName(orDefault(dto.getStoreName(), DEFAULT_STORE_NAME));
        String normalizedName = storeService.normalizeName(orDefault(dto.getStoreName(), DEFAULT_STORE_NAME));
        String code = storeService.normalizeCode(orDefault(dto.getStoreCode(), DEFAULT_STORE_CODE));
        String timezone = storeService.normalizeTimezone(orDefault(dto.getStoreTimezone(), DEFAULT_TIMEZONE));
        String countryCode = storeService.normalizeCountryCode(
                orDefault(dto.getStoreCountryCode(), DEFAULT_COUNTRY_CODE));

        LocalDateTime now = LocalDateTime.now(clock);

        // 1) Negocio
        Negocio negocio = negocioRepository.save(Negocio.builder()
                .nombre(dto.getNegocioNombre())
                .activo(true)
                .cashReconciliationEnabled(false)
                .build());

        // 2) Store Principal (created_by/updated_by null: actor cross-tenant)
        Store principal = storeRepository.saveAndFlush(Store.builder()
                .negocio(negocio)
                .name(name)
                .normalizedName(normalizedName)
                .code(code)
                .timezone(timezone)
                .active(true)
                .primaryStore(true)
                .address(trimToNull(dto.getStoreAddress()))
                .city(trimToNull(dto.getStoreCity()))
                .countryCode(countryCode)
                .cashReconciliationEnabled(false)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 3) Secuencia de numeración de la Store Principal.
        sequenceRepository.initializeIfAbsent(negocio.getId(), principal.getId());

        // 4) Primer ADMIN (se rechaza CAMARERO; se fuerza ADMIN).
        User admin = userRepository.saveAndFlush(User.builder()
                .nombre(dto.getAdminNombre())
                .username(normalizedUsername)
                .password(passwordEncoder.encode(dto.getAdminPassword()))
                .role(Role.ADMIN)
                .negocio(negocio)
                .defaultStore(principal)
                .activo(true)
                .tokenVersion(1)
                .build());

        // 5) Acceso a la Principal (assigned_by null: actor cross-tenant).
        accessRepository.saveAndFlush(UserStoreAccess.builder()
                .id(new UserStoreAccessId(admin.getId(), principal.getId()))
                .user(admin)
                .store(principal)
                .negocio(negocio)
                .active(true)
                .assignedAt(now)
                .assignedBy(null)
                .build());

        // 6) Auditoría (tenant explícito: el JWT del SUPER_ADMIN es otro).
        Long tenantId = negocio.getId();
        auditEventService.recordSuccessForTenant(tenantId, null,
                AuditEntityType.NEGOCIO, tenantId, AuditAction.TENANT_PROVISIONED,
                null, Map.of("nombre", negocio.getNombre(), "activo", true), null);
        auditEventService.recordSuccessForTenant(tenantId, principal.getId(),
                AuditEntityType.STORE, principal.getId(), AuditAction.STORE_CREATED,
                null, Map.of("name", principal.getName(), "code", principal.getCode(),
                        "primaryStore", true, "active", true), null);
        auditEventService.recordSuccessForTenant(tenantId, null,
                AuditEntityType.USER, admin.getId(), AuditAction.USER_CREATED,
                null, Map.of("username", admin.getUsername(), "role", admin.getRole(),
                        "defaultStoreId", principal.getId(), "activo", true), null);
        auditEventService.recordSuccessForTenant(tenantId, principal.getId(),
                AuditEntityType.USER_STORE_ACCESS, admin.getId(), AuditAction.USER_STORE_ACCESS_GRANTED,
                null, Map.of("userId", admin.getId(), "storeId", principal.getId(), "active", true), null);

        return TenantProvisionResponseDto.builder()
                .negocioId(tenantId)
                .negocioNombre(negocio.getNombre())
                .principalStore(toStoreResponse(principal))
                .admin(TenantProvisionResponseDto.AdminSummary.builder()
                        .id(admin.getId())
                        .nombre(admin.getNombre())
                        .username(admin.getUsername())
                        .role(admin.getRole())
                        .defaultStoreId(principal.getId())
                        .build())
                .build();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static StoreResponseDto toStoreResponse(Store store) {
        return StoreResponseDto.builder()
                .id(store.getId()).name(store.getName()).code(store.getCode())
                .timezone(store.getTimezone()).active(store.getActive())
                .primaryStore(store.getPrimaryStore()).address(store.getAddress())
                .city(store.getCity()).countryCode(store.getCountryCode())
                .cashReconciliationEnabled(store.getCashReconciliationEnabled()).build();
    }
}
