package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.*;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccessId;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.DuplicateResourceException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.repository.UserStoreAccessRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CRUD administrativo de usuarios (Fase 9, F9.4), bajo {@code /api/admin/users}.
 * Sustituye funcionalmente al CRUD antiguo de {@code /api/users} con contratos
 * multi-tienda: alta con Stores y default, edición sin cambio de tenant,
 * cambio de estado, reset de contraseña y auditoría. Nunca borra físicamente.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final NegocioRepository negocioRepository;
    private final StoreRepository storeRepository;
    private final UserStoreAccessRepository accessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEventService;
    private final AdminAuthorizationService adminAuth;
    private final CurrentUser currentUser;
    private final Clock clock;

    public UserAdminService(
            UserRepository userRepository,
            NegocioRepository negocioRepository,
            StoreRepository storeRepository,
            UserStoreAccessRepository accessRepository,
            PasswordEncoder passwordEncoder,
            AuditEventService auditEventService,
            AdminAuthorizationService adminAuth,
            CurrentUser currentUser,
            Clock clock) {
        this.userRepository = userRepository;
        this.negocioRepository = negocioRepository;
        this.storeRepository = storeRepository;
        this.accessRepository = accessRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditEventService = auditEventService;
        this.adminAuth = adminAuth;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<AdminUserResponseDto> list(
            Long tenantId, String q, Boolean active,
            com.harbeyescala.api_apuntalo.entity.Role role, int page, int size) {
        PaginationPolicy.validate(page, size);
        Pageable pageable = PageRequest.of(page, size);
        String query = (q == null || q.isBlank()) ? null : q.trim();
        Page<User> result = userRepository.searchAdmin(tenantId, active, role, query, pageable);
        List<AdminUserResponseDto> content = result.getContent().stream()
                .map(u -> AdminUserResponseDto.builder()
                        .id(u.getId())
                        .nombre(u.getNombre())
                        .username(u.getUsername())
                        .role(u.getRole())
                        .negocioId(tenantId)
                        .activo(u.getActivo())
                        .defaultStoreId(u.getDefaultStore() != null ? u.getDefaultStore().getId() : null)
                        .activeStoreCount(adminAuth.countActiveAccesses(u.getId(), tenantId))
                        .build())
                .toList();
        return new PageResponseDto<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isLast());
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponseDto findById(Long tenantId, Long id) {
        User user = userRepository.findWithDefaultStoreByIdAndNegocioId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        adminAuth.requireUserAdministrable(user, tenantId);
        return buildDetail(tenantId, user);
    }

    @Transactional
    public AdminUserDetailResponseDto create(Long tenantId, AdminUserCreateRequestDto dto) {
        adminAuth.requireRoleChangeAllowed(dto.getRole(), dto.getRole());

        String normalizedUsername = AuthService.normalizeUsername(dto.getUsername());
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese username");
        }

        Set<Long> storeIds = new LinkedHashSet<>(dto.getStoreIds());
        Long defaultStoreId = dto.getDefaultStoreId();
        if (!storeIds.contains(defaultStoreId)) {
            throw new ConflictException("DEFAULT_STORE_NOT_IN_ACTIVE_SET",
                    "La tienda predeterminada debe pertenecer a las tiendas asignadas");
        }

        List<Store> stores = storeRepository.findByIdInAndNegocioId(storeIds, tenantId);
        if (stores.size() != storeIds.size()) {
            throw new ResourceNotFoundException("Alguna tienda no existe en el negocio");
        }
        for (Store store : stores) {
            if (!Boolean.TRUE.equals(store.getActive())) {
                throw new ConflictException("STORE_INACTIVE",
                        "No se puede asignar una tienda inactiva: " + store.getName());
            }
            adminAuth.requireStoreAuthority(store.getId(), tenantId);
        }

        Negocio negocio = negocioRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
        Store defaultStore = stores.stream()
                .filter(s -> s.getId().equals(defaultStoreId)).findFirst().orElseThrow();

        LocalDateTime now = LocalDateTime.now(clock);
        User user = userRepository.saveAndFlush(User.builder()
                .nombre(dto.getNombre())
                .username(normalizedUsername)
                .password(passwordEncoder.encode(dto.getPassword()))
                .role(dto.getRole())
                .negocio(negocio)
                .defaultStore(defaultStore)
                .activo(true)
                .tokenVersion(1)
                .build());

        User assignedBy = adminAuth.actorBelongsToTenant(tenantId)
                ? userRepository.getReferenceById(currentUser.getUserId()) : null;
        for (Store store : stores) {
            accessRepository.save(UserStoreAccess.builder()
                    .id(new UserStoreAccessId(user.getId(), store.getId()))
                    .user(user)
                    .store(store)
                    .negocio(negocio)
                    .active(true)
                    .assignedAt(now)
                    .assignedBy(assignedBy)
                    .build());
        }
        accessRepository.flush();

        auditEventService.recordSuccessForTenant(tenantId, null, AuditEntityType.USER, user.getId(),
                AuditAction.USER_CREATED, null,
                Map.of("username", user.getUsername(), "role", user.getRole(),
                        "defaultStoreId", defaultStoreId, "storeIds", storeIds, "activo", true), null);
        for (Store store : stores) {
            auditEventService.recordSuccessForTenant(tenantId, store.getId(),
                    AuditEntityType.USER_STORE_ACCESS, user.getId(),
                    AuditAction.USER_STORE_ACCESS_GRANTED, null,
                    Map.of("userId", user.getId(), "storeId", store.getId(), "active", true), null);
        }

        User reloaded = userRepository.findWithDefaultStoreByIdAndNegocioId(user.getId(), tenantId)
                .orElseThrow();
        return buildDetail(tenantId, reloaded);
    }

    @Transactional
    public AdminUserDetailResponseDto update(Long tenantId, Long id, AdminUserUpdateRequestDto dto) {
        User user = userRepository.findByIdAndNegocioIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        adminAuth.requireUserAdministrable(user, tenantId);
        adminAuth.requireRoleChangeAllowed(user.getRole(), dto.getRole());

        String normalizedUsername = AuthService.normalizeUsername(dto.getUsername());
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(normalizedUsername, id)) {
            throw new DuplicateResourceException("Ya existe un usuario con ese username");
        }

        boolean roleChanged = user.getRole() != dto.getRole();
        com.harbeyescala.api_apuntalo.entity.Role previousRole = user.getRole();

        user.setNombre(dto.getNombre());
        user.setUsername(normalizedUsername);
        user.setRole(dto.getRole());
        if (roleChanged) {
            user.setTokenVersion(user.getTokenVersion() + 1);
        }
        User saved = userRepository.save(user);

        auditEventService.recordSuccessForTenant(tenantId, null, AuditEntityType.USER, id,
                AuditAction.USER_UPDATED, Map.of("nombre", user.getNombre(), "role", previousRole),
                Map.of("nombre", saved.getNombre(), "username", saved.getUsername(),
                        "role", saved.getRole()), null);
        if (roleChanged) {
            auditEventService.recordSuccessForTenant(tenantId, null, AuditEntityType.USER, id,
                    AuditAction.USER_ROLE_CHANGED, Map.of("role", previousRole),
                    Map.of("role", saved.getRole()), null);
        }

        User reloaded = userRepository.findWithDefaultStoreByIdAndNegocioId(id, tenantId).orElseThrow();
        return buildDetail(tenantId, reloaded);
    }

    @Transactional
    public AdminUserDetailResponseDto setActive(Long tenantId, Long id, boolean active) {
        User user = userRepository.findByIdAndNegocioIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        adminAuth.requireUserAdministrable(user, tenantId);

        if (!active) {
            if (Objects.equals(id, currentUser.getUserId())) {
                throw new ConflictException("CANNOT_DEACTIVATE_SELF",
                        "No puedes desactivarte a ti mismo");
            }
            adminAuth.requireNoOpenCashSession(id, tenantId);
        }

        if (Boolean.TRUE.equals(user.getActivo()) == active) {
            User reloaded = userRepository.findWithDefaultStoreByIdAndNegocioId(id, tenantId).orElseThrow();
            return buildDetail(tenantId, reloaded);
        }

        user.setActivo(active);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditEventService.recordSuccessForTenant(tenantId, null, AuditEntityType.USER, id,
                active ? AuditAction.USER_ACTIVATED : AuditAction.USER_DEACTIVATED,
                Map.of("activo", !active), Map.of("activo", active), null);

        User reloaded = userRepository.findWithDefaultStoreByIdAndNegocioId(id, tenantId).orElseThrow();
        return buildDetail(tenantId, reloaded);
    }

    @Transactional
    public void resetPassword(Long tenantId, Long id, String newPassword) {
        User user = userRepository.findByIdAndNegocioIdForUpdate(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        adminAuth.requireUserAdministrable(user, tenantId);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditEventService.recordSuccessForTenant(tenantId, null, AuditEntityType.USER, id,
                AuditAction.USER_PASSWORD_RESET, null, Map.of("passwordReset", true), null);
    }

    private AdminUserDetailResponseDto buildDetail(Long tenantId, User user) {
        Long defaultStoreId = user.getDefaultStore() != null ? user.getDefaultStore().getId() : null;
        List<UserStoreAccess> accesses = accessRepository.findAllByUserIdAndNegocioId(user.getId(), tenantId);
        List<UserStoreAccessResponseDto> stores = accesses.stream()
                .map(a -> {
                    Store s = a.getStore();
                    return UserStoreAccessResponseDto.builder()
                            .storeId(s.getId())
                            .storeName(s.getName())
                            .storeCode(s.getCode())
                            .timezone(s.getTimezone())
                            .storeActive(s.getActive())
                            .primaryStore(s.getPrimaryStore())
                            .defaultStore(s.getId().equals(defaultStoreId))
                            .activeStore(null)
                            .accessActive(a.getActive())
                            .build();
                })
                .toList();
        long activeCount = accesses.stream().filter(a -> Boolean.TRUE.equals(a.getActive())).count();
        return AdminUserDetailResponseDto.builder()
                .id(user.getId())
                .nombre(user.getNombre())
                .username(user.getUsername())
                .role(user.getRole())
                .negocioId(tenantId)
                .negocioNombre(user.getNegocio() != null ? user.getNegocio().getNombre() : null)
                .activo(user.getActivo())
                .defaultStoreId(defaultStoreId)
                .defaultStore(user.getDefaultStore() != null
                        ? TenantProvisioningService.toStoreResponse(user.getDefaultStore()) : null)
                .activeStoreCount(activeCount)
                .stores(stores)
                .build();
    }
}
