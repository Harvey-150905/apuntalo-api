package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.Role;

import java.util.Set;

/**
 * Reglas puras de autorización administrativa multi-tienda (Fase 9). No
 * dependen de Spring ni de la base de datos para poder probarse de forma
 * unitaria (JUnit sin contexto). {@link AdminAuthorizationService} las
 * consume y traduce a excepciones funcionales con códigos del catálogo.
 */
public final class AdminAuthorizationRules {

    private AdminAuthorizationRules() {
    }

    /**
     * {@code true} si todos los ids de {@code subset} están contenidos en
     * {@code superset}. El conjunto vacío es siempre subconjunto.
     */
    public static boolean isSubset(Set<Long> subset, Set<Long> superset) {
        if (subset == null || subset.isEmpty()) {
            return true;
        }
        if (superset == null) {
            return false;
        }
        return superset.containsAll(subset);
    }

    /**
     * Un ADMIN puede administrar al usuario objetivo solo si:
     * <ul>
     *   <li>el objetivo NO es SUPER_ADMIN, y</li>
     *   <li>todas las Stores activas del objetivo están contenidas en las
     *       Stores activas del propio ADMIN.</li>
     * </ul>
     * Un SUPER_ADMIN siempre puede administrar (dentro del tenant resuelto).
     */
    public static boolean canAdminister(
            Role actorRole,
            Role targetRole,
            Set<Long> actorActiveStoreIds,
            Set<Long> targetActiveStoreIds) {
        if (actorRole == Role.SUPER_ADMIN) {
            return true;
        }
        if (actorRole != Role.ADMIN) {
            return false;
        }
        if (targetRole == Role.SUPER_ADMIN) {
            return false;
        }
        return isSubset(targetActiveStoreIds, actorActiveStoreIds);
    }

    /**
     * La Store predeterminada debe pertenecer al conjunto de Stores activas
     * del usuario. Un conjunto activo vacío nunca es válido.
     */
    public static boolean isDefaultStoreInActiveSet(Long defaultStoreId, Set<Long> activeStoreIds) {
        if (defaultStoreId == null || activeStoreIds == null || activeStoreIds.isEmpty()) {
            return false;
        }
        return activeStoreIds.contains(defaultStoreId);
    }

    /**
     * Impide dejar a un usuario sin ninguna Store activa: retirar el acceso
     * indicado solo es válido si quedaría al menos una Store activa.
     */
    public static boolean canRevokeKeepingAtLeastOne(Set<Long> currentActiveStoreIds, Long storeIdToRevoke) {
        if (currentActiveStoreIds == null || storeIdToRevoke == null) {
            return false;
        }
        if (!currentActiveStoreIds.contains(storeIdToRevoke)) {
            // Nada que retirar; no deja al usuario sin Stores.
            return true;
        }
        return currentActiveStoreIds.size() > 1;
    }

    /**
     * Un ADMIN no puede escalar/otorgar el rol SUPER_ADMIN ni modificar a un
     * SUPER_ADMIN. Comprueba tanto el rol actual como el rol solicitado.
     */
    public static boolean isRoleChangeAllowed(Role actorRole, Role currentTargetRole, Role requestedRole) {
        if (actorRole == Role.SUPER_ADMIN) {
            return true;
        }
        if (actorRole != Role.ADMIN) {
            return false;
        }
        if (currentTargetRole == Role.SUPER_ADMIN || requestedRole == Role.SUPER_ADMIN) {
            return false;
        }
        return true;
    }
}
