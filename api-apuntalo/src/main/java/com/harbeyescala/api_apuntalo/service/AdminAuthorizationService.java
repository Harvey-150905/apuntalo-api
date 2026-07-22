package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.Negocio;
import com.harbeyescala.api_apuntalo.entity.Role;
import com.harbeyescala.api_apuntalo.entity.User;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.enums.CashSessionStatus;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.exception.ForbiddenOperationException;
import com.harbeyescala.api_apuntalo.exception.ResourceNotFoundException;
import com.harbeyescala.api_apuntalo.repository.CashSessionRepository;
import com.harbeyescala.api_apuntalo.repository.NegocioRepository;
import com.harbeyescala.api_apuntalo.repository.UserStoreAccessRepository;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio central de autorización administrativa multi-tienda (Fase 9,
 * F9.1). Concentra las reglas de plataforma vs tenant y el alcance de un
 * ADMIN limitado a sus Stores. Las reglas de conjunto puras viven en
 * {@link AdminAuthorizationRules}; aquí se resuelven contra la base de datos
 * y se traducen a excepciones funcionales con códigos del catálogo F9.
 */
@Service
public class AdminAuthorizationService {

    private final CurrentUser currentUser;
    private final UserStoreAccessRepository accessRepository;
    private final CashSessionRepository cashSessionRepository;
    private final NegocioRepository negocioRepository;

    public AdminAuthorizationService(
            CurrentUser currentUser,
            UserStoreAccessRepository accessRepository,
            CashSessionRepository cashSessionRepository,
            NegocioRepository negocioRepository) {
        this.currentUser = currentUser;
        this.accessRepository = accessRepository;
        this.cashSessionRepository = cashSessionRepository;
        this.negocioRepository = negocioRepository;
    }

    /**
     * Resuelve el tenant sobre el que actúa la operación administrativa.
     * <ul>
     *   <li>SUPER_ADMIN: puede indicar un {@code targetNegocioId} (operador
     *       de plataforma); si no lo indica, usa su propio tenant del JWT.</li>
     *   <li>ADMIN: siempre su tenant del JWT; si indica un
     *       {@code targetNegocioId} distinto se rechaza con 403.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Long resolveAdminTenantId(Optional<Long> targetNegocioId) {
        Long jwtTenantId = currentUser.getTenantId();
        if (currentUser.isSuperAdmin()) {
            Long resolved = targetNegocioId.orElse(jwtTenantId);
            Negocio negocio = negocioRepository.findById(resolved)
                    .orElseThrow(() -> new ResourceNotFoundException("Negocio no encontrado"));
            return negocio.getId();
        }
        if (targetNegocioId.isPresent() && !targetNegocioId.get().equals(jwtTenantId)) {
            throw new ForbiddenOperationException(
                    "TENANT_SCOPE_FORBIDDEN",
                    "No puedes administrar recursos de otro negocio");
        }
        return jwtTenantId;
    }

    /**
     * Un ADMIN solo puede operar sobre una Store si tiene acceso activo a
     * ella; un SUPER_ADMIN no requiere acceso (operador de plataforma).
     */
    @Transactional(readOnly = true)
    public void requireStoreAuthority(Long storeId, Long tenantId) {
        if (currentUser.isSuperAdmin()) {
            return;
        }
        boolean authorized = accessRepository.existsByUserIdAndStoreIdAndNegocioIdAndActiveTrue(
                currentUser.getUserId(), storeId, tenantId);
        if (!authorized) {
            throw new ForbiddenOperationException(
                    "STORE_AUTHORITY_REQUIRED",
                    "No tienes autoridad sobre la tienda indicada");
        }
    }

    /**
     * Verifica que el actor puede administrar al usuario objetivo. Un ADMIN
     * solo puede administrar usuarios cuyo conjunto de Stores activas esté
     * contenido en el suyo y que no sean SUPER_ADMIN (spec 4.1 y F9.1).
     */
    @Transactional(readOnly = true)
    public void requireUserAdministrable(User targetUser, Long tenantId) {
        Role actorRole = currentUser.getRole();
        if (actorRole == Role.SUPER_ADMIN) {
            return;
        }
        Set<Long> actorStores = activeStoreIds(currentUser.getUserId(), tenantId);
        Set<Long> targetStores = activeStoreIds(targetUser.getId(), tenantId);
        boolean allowed = AdminAuthorizationRules.canAdminister(
                actorRole, targetUser.getRole(), actorStores, targetStores);
        if (!allowed) {
            throw new ForbiddenOperationException(
                    "USER_NOT_ADMINISTRABLE",
                    "No tienes autoridad para administrar a este usuario");
        }
    }

    /**
     * Impide operaciones (desactivación, retirada de acceso, cambio de
     * Store) mientras el usuario sea responsable de una sesión de caja
     * abierta en cualquier Store del tenant (unicidad tenant-wide).
     */
    @Transactional(readOnly = true)
    public void requireNoOpenCashSession(Long userId, Long tenantId) {
        boolean hasOpen = cashSessionRepository.existsByOpenedByIdAndNegocioIdAndStatus(
                userId, tenantId, CashSessionStatus.OPEN);
        if (hasOpen) {
            throw new ConflictException(
                    "USER_HAS_OPEN_CASH_SESSION",
                    "El usuario es responsable de una sesión de caja abierta");
        }
    }

    /**
     * Conjunto de ids de Stores con acceso activo del usuario en el tenant.
     */
    @Transactional(readOnly = true)
    public Set<Long> activeStoreIds(Long userId, Long tenantId) {
        return accessRepository.findAllByUserIdAndNegocioIdAndActiveTrue(userId, tenantId).stream()
                .map(access -> access.getStore().getId())
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public long countActiveAccesses(Long userId, Long tenantId) {
        return accessRepository.countByUserIdAndNegocioIdAndActiveTrue(userId, tenantId);
    }

    /**
     * Valida que la Store predeterminada pertenezca al conjunto de accesos
     * activos indicado; si no, lanza el código del catálogo.
     */
    public void validateDefaultStoreInActiveSet(Long defaultStoreId, Set<Long> activeStoreIds) {
        if (!AdminAuthorizationRules.isDefaultStoreInActiveSet(defaultStoreId, activeStoreIds)) {
            throw new ConflictException(
                    "DEFAULT_STORE_NOT_IN_ACTIVE_SET",
                    "La tienda predeterminada debe pertenecer a los accesos activos del usuario");
        }
    }

    /**
     * Impide dejar al usuario sin ninguna Store activa al retirar un acceso.
     */
    public void ensureNotLastActiveAccess(Set<Long> currentActiveStoreIds, Long storeIdToRevoke) {
        if (!AdminAuthorizationRules.canRevokeKeepingAtLeastOne(currentActiveStoreIds, storeIdToRevoke)) {
            throw new ConflictException(
                    "LAST_ACTIVE_STORE_ACCESS",
                    "No se puede retirar el último acceso activo del usuario");
        }
    }

    /**
     * Impide a un ADMIN escalar el rol a SUPER_ADMIN o modificar a un
     * SUPER_ADMIN.
     */
    public void requireRoleChangeAllowed(Role currentTargetRole, Role requestedRole) {
        if (!AdminAuthorizationRules.isRoleChangeAllowed(
                currentUser.getRole(), currentTargetRole, requestedRole)) {
            throw new ForbiddenOperationException(
                    "ROLE_ESCALATION_FORBIDDEN",
                    "No puedes asignar o modificar ese rol");
        }
    }

    /**
     * {@code assignedBy} para nuevas filas de acceso: el actor si pertenece
     * al mismo tenant que la fila; {@code null} si es un SUPER_ADMIN de
     * plataforma actuando sobre otro tenant (FK tenant-safe).
     */
    public boolean actorBelongsToTenant(Long tenantId) {
        return currentUser.getTenantId().equals(tenantId);
    }
}
