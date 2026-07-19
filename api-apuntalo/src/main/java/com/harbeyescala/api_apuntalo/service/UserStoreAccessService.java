package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.dto.UserStoreAccessResponseDto;
import com.harbeyescala.api_apuntalo.entity.Store;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccessId;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.exception.ForbiddenOperationException;
import com.harbeyescala.api_apuntalo.exception.StoreNotFoundException;
import com.harbeyescala.api_apuntalo.repository.StoreRepository;
import com.harbeyescala.api_apuntalo.repository.UserRepository;
import com.harbeyescala.api_apuntalo.repository.UserStoreAccessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserStoreAccessService {
    private final UserStoreAccessRepository accessRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public UserStoreAccessService(
            UserStoreAccessRepository accessRepository,
            StoreRepository storeRepository,
            UserRepository userRepository,
            Clock clock
    ) {
        this.accessRepository = accessRepository;
        this.storeRepository = storeRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserStoreAccessResponseDto> listAuthorizedActiveStores(
            Long userId, Long negocioId, Long activeStoreId) {
        Long defaultStoreId = userRepository.findWithDefaultStoreByIdAndNegocioId(userId, negocioId)
                .map(User::getDefaultStore).map(Store::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return accessRepository.findAuthorizedActiveStores(userId, negocioId).stream()
                .map(access -> toResponse(access, defaultStoreId, activeStoreId))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean hasActiveAccess(Long userId, Long storeId, Long negocioId) {
        return accessRepository.existsByUserIdAndStoreIdAndNegocioIdAndActiveTrue(
                userId, storeId, negocioId);
    }

    @Transactional(readOnly = true)
    public Store findValidActiveStore(Long userId, Long storeId, Long negocioId) {
        return accessRepository.findValidActiveStoreAccess(userId, storeId, negocioId)
                .map(UserStoreAccess::getStore)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Store resolveStoreForSwitch(Long userId, Long storeId, Long negocioId) {
        Store store = storeRepository.findByIdAndNegocioId(storeId, negocioId)
                .orElseThrow(StoreNotFoundException::new);
        UserStoreAccess access = accessRepository
                .findByUserIdAndStoreIdAndNegocioId(userId, storeId, negocioId)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "STORE_ACCESS_DENIED", "No tienes acceso a la tienda solicitada"));
        if (!Boolean.TRUE.equals(access.getActive())) {
            throw new ForbiddenOperationException(
                    "STORE_ACCESS_DENIED", "No tienes acceso a la tienda solicitada");
        }
        if (!Boolean.TRUE.equals(store.getActive())) {
            throw new ConflictException("STORE_INACTIVE", "La tienda no está activa");
        }
        return store;
    }

    @Transactional(readOnly = true)
    public Store getValidDefaultStore(Long userId, Long negocioId) {
        User user = userRepository.findWithDefaultStoreByIdAndNegocioId(userId, negocioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Store defaultStore = user.getDefaultStore();
        verifyDefaultStoreAccess(userId, defaultStore.getId(), negocioId);
        if (!Boolean.TRUE.equals(defaultStore.getActive())) {
            throw new ConflictException("DEFAULT_STORE_INACTIVE", "La tienda predeterminada no está activa");
        }
        return defaultStore;
    }

    @Transactional(readOnly = true)
    public void verifyDefaultStoreAccess(Long userId, Long storeId, Long negocioId) {
        if (!hasActiveAccess(userId, storeId, negocioId)) {
            throw new ConflictException(
                    "DEFAULT_STORE_ACCESS_INVALID",
                    "La tienda predeterminada no pertenece a los accesos activos del usuario");
        }
    }

    @Transactional(readOnly = true)
    public Store getPrimaryStore(Long negocioId) {
        return storeRepository.findByNegocioIdAndPrimaryStoreTrue(negocioId)
                .orElseThrow(() -> new ConflictException(
                        "PRIMARY_STORE_NOT_FOUND", "El negocio no tiene una tienda Principal"));
    }

    @Transactional
    public UserStoreAccess createPrincipalAccessForNewUser(User user, Store primaryStore, User assignedBy) {
        Long negocioId = user.getNegocio().getId();
        if (!negocioId.equals(primaryStore.getNegocio().getId())) {
            throw new ConflictException("STORE_TENANT_MISMATCH", "La tienda no pertenece al negocio del usuario");
        }
        if (assignedBy != null && !negocioId.equals(assignedBy.getNegocio().getId())) {
            throw new ConflictException("ASSIGNER_TENANT_MISMATCH", "El actor no pertenece al negocio del usuario");
        }

        UserStoreAccess access = UserStoreAccess.builder()
                .id(new UserStoreAccessId(user.getId(), primaryStore.getId()))
                .user(user)
                .store(primaryStore)
                .negocio(user.getNegocio())
                .active(true)
                .assignedAt(LocalDateTime.now(clock))
                .assignedBy(assignedBy)
                .build();
        return accessRepository.saveAndFlush(access);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyAccess(Long userId, Long negocioId) {
        return accessRepository.existsByUserIdAndNegocioId(userId, negocioId);
    }

    private UserStoreAccessResponseDto toResponse(
            UserStoreAccess access, Long defaultStoreId, Long activeStoreId) {
        Store store = access.getStore();
        return UserStoreAccessResponseDto.builder()
                .storeId(store.getId())
                .storeName(store.getName())
                .storeCode(store.getCode())
                .timezone(store.getTimezone())
                .storeActive(store.getActive())
                .primaryStore(store.getPrimaryStore())
                .defaultStore(store.getId().equals(defaultStoreId))
                .activeStore(store.getId().equals(activeStoreId))
                .accessActive(access.getActive())
                .build();
    }
}
