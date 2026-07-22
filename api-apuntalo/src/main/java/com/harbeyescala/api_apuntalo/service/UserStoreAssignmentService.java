package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.UserStoreAccessResponseDto;
import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccessId;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.repository.UserStoreAccessRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gestión transaccional de accesos usuario-tienda y Store predeterminada
 * (Fase 9, F9.5). Bloquea primero el usuario y luego opera sobre sus accesos;
 * reactiva filas inactivas en vez de duplicar; preserva siempre al menos una
 * Store activa y una Store predeterminada dentro del conjunto activo.
 */
@Service
public class UserStoreAssignmentService {

    private final UserRepository userRepository;
    private final NegocioRepository negocioRepository;
    private final StoreRepository storeRepository;
    private final UserStoreAccessRepository accessRepository;
    private final AuditEventService auditEventService;
    private final AdminAuthorizationService adminAuth;
    private final CurrentUser currentUser;
    private final Clock clock;

    public UserStoreAssignmentService(
            UserRepository userRepository,
            NegocioRepository negocioRepository,
            StoreRepository storeRepository,
            UserStoreAccessRepository accessRepository,
            AuditEventService auditEventService,
            AdminAuthorizationService adminAuth,
            CurrentUser currentUser,
            Clock clock) {
        this.userRepository = userRepository;
        this.negocioRepository = negocioRepository;
        this.storeRepository = storeRepository;
        this.accessRepository = accessRepository;
        this.auditEventService = auditEventService;
        this.adminAuth = adminAuth;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserStoreAccessResponseDto> list(Long tenantId, Long userId) {
        User user = userRepository.findWithDefaultStoreByIdAndNegocioId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        adminAuth.requireUserAdministrable(user, tenantId);
        return mapAccesses(userId, tenantId, defaultStoreId(user));
    }

    @Transactional
    public List<UserStoreAccessResponseDto> batchAssign(
            Long tenantId, Long userId, List<Long> activeStoreIds, Long defaultStoreId) {
        User user = lockUser(userId, tenantId);
        adminAuth.requireUserAdministrable(user, tenantId);

        Set<Long> targetIds = new LinkedHashSet<>(activeStoreIds);
        if (!targetIds.contains(defaultStoreId)) {
            throw new ConflictException("DEFAULT_STORE_NOT_IN_ACTIVE_SET",
                    "La tienda predeterminada debe pertenecer al conjunto activo");
        }
        List<Store> stores = loadActiveStores(targetIds, tenantId);
        Map<Long, Store> storeById = stores.stream()
                .collect(Collectors.toMap(Store::getId, s -> s));
        for (Store store : stores) {
            adminAuth.requireStoreAuthority(store.getId(), tenantId);
        }

        Negocio negocio = negocioRepository.getReferenceById(tenantId);
        LocalDateTime now = LocalDateTime.now(clock);
        User assignedBy = actorRef(tenantId);

        List<UserStoreAccess> existing = accessRepository.findAllByUserIdAndNegocioId(userId, tenantId);
        Map<Long, UserStoreAccess> existingByStore = new LinkedHashMap<>();
        existing.forEach(a -> existingByStore.put(a.getStore().getId(), a));

        // Activar/crear el conjunto objetivo (reactivando filas inactivas).
        for (Long storeId : targetIds) {
            UserStoreAccess access = existingByStore.get(storeId);
            if (access == null) {
                access = UserStoreAccess.builder()
                        .id(new UserStoreAccessId(userId, storeId))
                        .user(user)
                        .store(storeById.get(storeId))
                        .negocio(negocio)
                        .active(true)
                        .assignedAt(now)
                        .assignedBy(assignedBy)
                        .build();
                accessRepository.save(access);
                auditEventService.recordSuccessForTenant(tenantId, storeId,
                        AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_GRANTED,
                        null, Map.of("userId", userId, "storeId", storeId, "active", true), null);
            } else if (!Boolean.TRUE.equals(access.getActive())) {
                access.setActive(true);
                accessRepository.save(access);
                auditEventService.recordSuccessForTenant(tenantId, storeId,
                        AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_GRANTED,
                        Map.of("active", false), Map.of("active", true), null);
            }
        }

        // Revocar los accesos activos que quedan fuera del conjunto objetivo.
        for (UserStoreAccess access : existing) {
            Long storeId = access.getStore().getId();
            if (!targetIds.contains(storeId) && Boolean.TRUE.equals(access.getActive())) {
                access.setActive(false);
                accessRepository.save(access);
                auditEventService.recordSuccessForTenant(tenantId, storeId,
                        AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_REVOKED,
                        Map.of("active", true), Map.of("active", false), null);
            }
        }

        applyDefaultStore(tenantId, user, defaultStoreId, storeById.get(defaultStoreId));
        accessRepository.flush();

        auditEventService.recordSuccessForTenant(tenantId, null,
                AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_UPDATED,
                null, Map.of("userId", userId, "activeStoreIds", targetIds,
                        "defaultStoreId", defaultStoreId), null);

        return mapAccesses(userId, tenantId, defaultStoreId);
    }

    @Transactional
    public List<UserStoreAccessResponseDto> assignOne(
            Long tenantId, Long userId, Long storeId, boolean makeDefault) {
        User user = lockUser(userId, tenantId);
        adminAuth.requireUserAdministrable(user, tenantId);
        adminAuth.requireStoreAuthority(storeId, tenantId);
        Store store = loadActiveStore(storeId, tenantId);

        UserStoreAccess access = accessRepository
                .findByUserIdAndStoreIdAndNegocioId(userId, storeId, tenantId)
                .orElse(null);
        if (access == null) {
            access = UserStoreAccess.builder()
                    .id(new UserStoreAccessId(userId, storeId))
                    .user(user)
                    .store(store)
                    .negocio(negocioRepository.getReferenceById(tenantId))
                    .active(true)
                    .assignedAt(LocalDateTime.now(clock))
                    .assignedBy(actorRef(tenantId))
                    .build();
            accessRepository.save(access);
            auditEventService.recordSuccessForTenant(tenantId, storeId,
                    AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_GRANTED,
                    null, Map.of("userId", userId, "storeId", storeId, "active", true), null);
        } else if (!Boolean.TRUE.equals(access.getActive())) {
            access.setActive(true);
            accessRepository.save(access);
            auditEventService.recordSuccessForTenant(tenantId, storeId,
                    AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_GRANTED,
                    Map.of("active", false), Map.of("active", true), null);
        }

        if (makeDefault) {
            applyDefaultStore(tenantId, user, storeId, store);
        }
        accessRepository.flush();
        return mapAccesses(userId, tenantId, defaultStoreId(userRepository
                .findWithDefaultStoreByIdAndNegocioId(userId, tenantId).orElseThrow()));
    }

    @Transactional
    public List<UserStoreAccessResponseDto> revoke(
            Long tenantId, Long userId, Long storeId, Long replacementDefaultStoreId) {
        User user = lockUser(userId, tenantId);
        adminAuth.requireUserAdministrable(user, tenantId);

        UserStoreAccess access = accessRepository
                .findByUserIdAndStoreIdAndNegocioId(userId, storeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("El acceso no existe"));
        if (!Boolean.TRUE.equals(access.getActive())) {
            return mapAccesses(userId, tenantId, defaultStoreId(user));
        }

        // No dejar al usuario sin ninguna Store activa.
        Set<Long> activeIds = adminAuth.activeStoreIds(userId, tenantId);
        adminAuth.ensureNotLastActiveAccess(activeIds, storeId);

        // No retirar el acceso de un responsable de caja abierta.
        adminAuth.requireNoOpenCashSession(userId, tenantId);

        boolean defaultChanged = false;
        Long currentDefaultId = defaultStoreId(user);
        if (Objects.equals(currentDefaultId, storeId)) {
            if (replacementDefaultStoreId == null || replacementDefaultStoreId.equals(storeId)) {
                throw new ConflictException("REPLACEMENT_DEFAULT_STORE_REQUIRED",
                        "Debes indicar una nueva tienda predeterminada al retirar la actual");
            }
            Store replacement = loadActiveStore(replacementDefaultStoreId, tenantId);
            applyDefaultStore(tenantId, user, replacementDefaultStoreId, replacement);
            defaultChanged = true;
        }

        access.setActive(false);
        accessRepository.save(access);
        accessRepository.flush();

        auditEventService.recordSuccessForTenant(tenantId, storeId,
                AuditEntityType.USER_STORE_ACCESS, userId, AuditAction.USER_STORE_ACCESS_REVOKED,
                Map.of("active", true), Map.of("active", false,
                        "replacementDefaultStoreId", defaultChanged ? replacementDefaultStoreId : null), null);

        return mapAccesses(userId, tenantId, defaultStoreId(userRepository
                .findWithDefaultStoreByIdAndNegocioId(userId, tenantId).orElseThrow()));
    }

    @Transactional
    public List<UserStoreAccessResponseDto> setDefaultStore(
            Long tenantId, Long userId, Long defaultStoreId) {
        User user = lockUser(userId, tenantId);
        adminAuth.requireUserAdministrable(user, tenantId);
        Store store = storeRepository.findByIdAndNegocioId(defaultStoreId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
        applyDefaultStore(tenantId, user, defaultStoreId, store);
        userRepository.flush();
        return mapAccesses(userId, tenantId, defaultStoreId);
    }

    // --- helpers ---

    private void applyDefaultStore(Long tenantId, User user, Long defaultStoreId, Store defaultStore) {
        Set<Long> activeIds = adminAuth.activeStoreIds(user.getId(), tenantId);
        adminAuth.validateDefaultStoreInActiveSet(defaultStoreId, activeIds);
        if (!Boolean.TRUE.equals(defaultStore.getActive())) {
            throw new ConflictException("DEFAULT_STORE_INACTIVE", "La tienda predeterminada no está activa");
        }
        Long previous = defaultStoreId(user);
        if (Objects.equals(previous, defaultStoreId)) {
            return;
        }
        user.setDefaultStore(defaultStore);
        userRepository.save(user);
        auditEventService.recordSuccessForTenant(tenantId, null,
                AuditEntityType.USER, user.getId(), AuditAction.USER_DEFAULT_STORE_CHANGED,
                Map.of("defaultStoreId", previous), Map.of("defaultStoreId", defaultStoreId), null);
    }

    private User lockUser(Long userId, Long tenantId) {
        return userRepository.findWithDefaultStoreByIdAndNegocioIdForUpdate(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private List<Store> loadActiveStores(Set<Long> ids, Long tenantId) {
        List<Store> stores = storeRepository.findByIdInAndNegocioId(ids, tenantId);
        if (stores.size() != ids.size()) {
            throw new ResourceNotFoundException("Alguna tienda no existe en el negocio");
        }
        for (Store store : stores) {
            if (!Boolean.TRUE.equals(store.getActive())) {
                throw new ConflictException("STORE_INACTIVE",
                        "No se puede asignar una tienda inactiva: " + store.getName());
            }
        }
        return stores;
    }

    private Store loadActiveStore(Long storeId, Long tenantId) {
        Store store = storeRepository.findByIdAndNegocioId(storeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda no encontrada"));
        if (!Boolean.TRUE.equals(store.getActive())) {
            throw new ConflictException("STORE_INACTIVE", "La tienda no está activa");
        }
        return store;
    }

    private User actorRef(Long tenantId) {
        return adminAuth.actorBelongsToTenant(tenantId)
                ? userRepository.getReferenceById(currentUser.getUserId()) : null;
    }

    private static Long defaultStoreId(User user) {
        return user.getDefaultStore() != null ? user.getDefaultStore().getId() : null;
    }

    private List<UserStoreAccessResponseDto> mapAccesses(Long userId, Long tenantId, Long defaultStoreId) {
        return accessRepository.findAllByUserIdAndNegocioId(userId, tenantId).stream()
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
    }
}
