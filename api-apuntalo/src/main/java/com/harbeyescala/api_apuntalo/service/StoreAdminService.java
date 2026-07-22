package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.dto.StoreCreateRequestDto;
import com.harbeyescala.api_apuntalo.dto.StoreResponseDto;
import com.harbeyescala.api_apuntalo.dto.StoreUpdateRequestDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccessId;
import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.entity.enums.TicketStatus;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.*;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * CRUD administrativo de Stores (Fase 9, F9.3). Nunca borra físicamente: las
 * Stores se activan/desactivan para conservar el histórico. La creación es
 * transaccional con su secuencia de numeración; un ADMIN recibe acceso activo
 * a la nueva Store conservando su Store predeterminada.
 */
@Service
public class StoreAdminService {

    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final NegocioRepository negocioRepository;
    private final UserRepository userRepository;
    private final UserStoreAccessRepository accessRepository;
    private final TicketNumberSequenceRepository sequenceRepository;
    private final CashSessionRepository cashSessionRepository;
    private final TicketRepository ticketRepository;
    private final AuditEventService auditEventService;
    private final AdminAuthorizationService adminAuth;
    private final CurrentUser currentUser;
    private final Clock clock;

    public StoreAdminService(
            StoreRepository storeRepository,
            StoreService storeService,
            NegocioRepository negocioRepository,
            UserRepository userRepository,
            UserStoreAccessRepository accessRepository,
            TicketNumberSequenceRepository sequenceRepository,
            CashSessionRepository cashSessionRepository,
            TicketRepository ticketRepository,
            AuditEventService auditEventService,
            AdminAuthorizationService adminAuth,
            CurrentUser currentUser,
            Clock clock) {
        this.storeRepository = storeRepository;
        this.storeService = storeService;
        this.negocioRepository = negocioRepository;
        this.userRepository = userRepository;
        this.accessRepository = accessRepository;
        this.sequenceRepository = sequenceRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.ticketRepository = ticketRepository;
        this.auditEventService = auditEventService;
        this.adminAuth = adminAuth;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<StoreResponseDto> list(
            Long tenantId, String q, Boolean active, int page, int size) {
        PaginationPolicy.validate(page, size);
        Pageable pageable = PageRequest.of(page, size);
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<Store> result = currentUser.isSuperAdmin()
                ? storeRepository.searchAdmin(tenantId, active, query, pageable)
                : storeRepository.searchAdminAuthorized(tenantId, active, query, currentUser.getUserId(), pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public StoreResponseDto findById(Long tenantId, Long id) {
        Store store = storeRepository.findByIdAndNegocioId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
        adminAuth.requireStoreAuthority(id, tenantId);
        return toResponse(store);
    }

    @Transactional
    public StoreResponseDto create(Long tenantId, StoreCreateRequestDto dto) {
        String name = storeService.cleanName(dto.getName());
        String normalizedName = storeService.normalizeName(dto.getName());
        String code = storeService.normalizeCode(dto.getCode());
        String timezone = storeService.normalizeTimezone(dto.getTimezone());
        String countryCode = storeService.normalizeCountryCode(dto.getCountryCode());

        if (storeRepository.existsByNegocioIdAndNormalizedName(tenantId, normalizedName)) {
            throw new ConflictException("STORE_NAME_ALREADY_EXISTS", "Ya existe una tienda con ese nombre");
        }
        if (storeRepository.existsByNegocioIdAndCode(tenantId, code)) {
            throw new ConflictException("STORE_CODE_ALREADY_EXISTS", "Ya existe una tienda con ese código");
        }

        Negocio negocio = negocioRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));

        LocalDateTime now = LocalDateTime.now(clock);
        boolean actorInTenant = adminAuth.actorBelongsToTenant(tenantId);
        User actor = actorInTenant
                ? userRepository.getReferenceById(currentUser.getUserId()) : null;

        Store store = storeRepository.saveAndFlush(Store.builder()
                .negocio(negocio)
                .name(name)
                .normalizedName(normalizedName)
                .code(code)
                .timezone(timezone)
                .active(true)
                .primaryStore(false)
                .address(trimToNull(dto.getAddress()))
                .city(trimToNull(dto.getCity()))
                .countryCode(countryCode)
                .cashReconciliationEnabled(Boolean.TRUE.equals(negocio.getCashReconciliationEnabled()))
                .createdAt(now)
                .updatedAt(now)
                .createdBy(actor)
                .updatedBy(actor)
                .build());

        sequenceRepository.initializeIfAbsent(tenantId, store.getId());

        // Un ADMIN que crea una Store recibe acceso activo a ella (conserva su default).
        boolean grantedToActor = false;
        if (actorInTenant && currentUser.getRole() == Role.ADMIN) {
            accessRepository.saveAndFlush(UserStoreAccess.builder()
                    .id(new UserStoreAccessId(currentUser.getUserId(), store.getId()))
                    .user(actor)
                    .store(store)
                    .negocio(negocio)
                    .active(true)
                    .assignedAt(now)
                    .assignedBy(actor)
                    .build());
            grantedToActor = true;
        }

        auditEventService.recordSuccessForTenant(tenantId, store.getId(),
                AuditEntityType.STORE, store.getId(), AuditAction.STORE_CREATED,
                null, Map.of("name", store.getName(), "code", store.getCode(),
                        "primaryStore", false, "active", true), null);
        if (grantedToActor) {
            auditEventService.recordSuccessForTenant(tenantId, store.getId(),
                    AuditEntityType.USER_STORE_ACCESS, currentUser.getUserId(),
                    AuditAction.USER_STORE_ACCESS_GRANTED,
                    null, Map.of("userId", currentUser.getUserId(), "storeId", store.getId(),
                            "active", true, "reason", "STORE_CREATOR"), null);
        }
        return toResponse(store);
    }

    @Transactional
    public StoreResponseDto update(Long tenantId, Long id, StoreUpdateRequestDto dto) {
        adminAuth.requireStoreAuthority(id, tenantId);
        Store store = storeRepository.findByIdAndNegocioId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        String name = storeService.cleanName(dto.getName());
        String normalizedName = storeService.normalizeName(dto.getName());
        String code = storeService.normalizeCode(dto.getCode());
        String timezone = storeService.normalizeTimezone(dto.getTimezone());
        String countryCode = storeService.normalizeCountryCode(dto.getCountryCode());

        if (storeRepository.existsByNegocioIdAndNormalizedNameAndIdNot(tenantId, normalizedName, id)) {
            throw new ConflictException("STORE_NAME_ALREADY_EXISTS", "Ya existe una tienda con ese nombre");
        }
        if (storeRepository.existsByNegocioIdAndCodeAndIdNot(tenantId, code, id)) {
            throw new ConflictException("STORE_CODE_ALREADY_EXISTS", "Ya existe una tienda con ese código");
        }

        Map<String, Object> before = Map.of("name", store.getName(), "code", store.getCode(),
                "timezone", store.getTimezone(), "countryCode", store.getCountryCode());

        store.setName(name);
        store.setNormalizedName(normalizedName);
        store.setCode(code);
        store.setTimezone(timezone);
        store.setCountryCode(countryCode);
        store.setAddress(trimToNull(dto.getAddress()));
        store.setCity(trimToNull(dto.getCity()));
        store.setUpdatedAt(LocalDateTime.now(clock));
        if (adminAuth.actorBelongsToTenant(tenantId)) {
            store.setUpdatedBy(userRepository.getReferenceById(currentUser.getUserId()));
        }
        Store saved = storeRepository.save(store);

        auditEventService.recordSuccessForTenant(tenantId, id, AuditEntityType.STORE, id,
                AuditAction.STORE_UPDATED, before,
                Map.of("name", saved.getName(), "code", saved.getCode(),
                        "timezone", saved.getTimezone(), "countryCode", saved.getCountryCode()), null);
        return toResponse(saved);
    }

    @Transactional
    public StoreResponseDto setActive(Long tenantId, Long id, boolean active) {
        adminAuth.requireStoreAuthority(id, tenantId);
        Store store = storeRepository.findByIdAndNegocioIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));

        boolean previous = Boolean.TRUE.equals(store.getActive());
        if (previous == active) {
            return toResponse(store);
        }

        if (!active) {
            if (Boolean.TRUE.equals(store.getPrimaryStore())) {
                throw new ConflictException("STORE_IS_PRIMARY_CANNOT_DISABLE",
                        "La tienda Principal no puede desactivarse");
            }
            if (cashSessionRepository.existsByNegocioIdAndStoreIdAndStatus(
                    tenantId, id, CashSessionStatus.OPEN)) {
                throw new ConflictException("STORE_HAS_OPEN_CASH_SESSIONS",
                        "No se puede desactivar una tienda con sesiones de caja abiertas");
            }
            if (ticketRepository.existsByNegocioIdAndStoreIdAndStatus(
                    tenantId, id, TicketStatus.OPEN)) {
                throw new ConflictException("STORE_HAS_OPEN_TICKETS",
                        "No se puede desactivar una tienda con tickets abiertos");
            }
            if (userRepository.existsByDefaultStore_IdAndActivoTrue(id)) {
                throw new ConflictException("STORE_IS_DEFAULT_OF_ACTIVE_USER",
                        "La tienda es la predeterminada de un usuario activo");
            }
            if (accessRepository.existsActiveUserStrandedByStoreDisable(tenantId, id)) {
                throw new ConflictException("STORE_DISABLE_WOULD_STRAND_USERS",
                        "Desactivar la tienda dejaría a algún usuario sin tiendas activas");
            }
        }

        store.setActive(active);
        store.setUpdatedAt(LocalDateTime.now(clock));
        if (adminAuth.actorBelongsToTenant(tenantId)) {
            store.setUpdatedBy(userRepository.getReferenceById(currentUser.getUserId()));
        }
        Store saved = storeRepository.save(store);

        auditEventService.recordSuccessForTenant(tenantId, id, AuditEntityType.STORE, id,
                active ? AuditAction.STORE_ACTIVATED : AuditAction.STORE_DEACTIVATED,
                Map.of("active", previous), Map.of("active", active), null);
        return toResponse(saved);
    }

    private PageResponseDto<StoreResponseDto> toPage(Page<Store> result) {
        return new PageResponseDto<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isLast());
    }

    private StoreResponseDto toResponse(Store store) {
        return TenantProvisioningService.toStoreResponse(store);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
