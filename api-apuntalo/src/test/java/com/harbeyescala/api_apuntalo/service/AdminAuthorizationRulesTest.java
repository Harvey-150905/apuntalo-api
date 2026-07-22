package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.Role;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias puras (sin contexto Spring) de las reglas de
 * autorización administrativa multi-tienda introducidas en la Fase 9
 * ({@link AdminAuthorizationRules}).
 */
class AdminAuthorizationRulesTest {

    // ---- isSubset -------------------------------------------------------

    @Test
    void emptySubsetIsAlwaysContained() {
        assertTrue(AdminAuthorizationRules.isSubset(Set.of(), Set.of(1L, 2L)));
        assertTrue(AdminAuthorizationRules.isSubset(null, Set.of()));
    }

    @Test
    void nonEmptySubsetRequiresSuperset() {
        assertTrue(AdminAuthorizationRules.isSubset(Set.of(1L), Set.of(1L, 2L)));
        assertFalse(AdminAuthorizationRules.isSubset(Set.of(1L, 3L), Set.of(1L, 2L)));
        assertFalse(AdminAuthorizationRules.isSubset(Set.of(1L), null));
    }

    // ---- canAdminister ----------------------------------------------------

    @Test
    void superAdminCanAlwaysAdminister() {
        assertTrue(AdminAuthorizationRules.canAdminister(
                Role.SUPER_ADMIN, Role.SUPER_ADMIN, Set.of(), Set.of(99L)));
        assertTrue(AdminAuthorizationRules.canAdminister(
                Role.SUPER_ADMIN, Role.ADMIN, Set.of(1L), Set.of(99L)));
    }

    @Test
    void camareroCanNeverAdminister() {
        assertFalse(AdminAuthorizationRules.canAdminister(
                Role.CAMARERO, Role.CAMARERO, Set.of(1L), Set.of(1L)));
    }

    @Test
    void adminCannotAdministerSuperAdmin() {
        assertFalse(AdminAuthorizationRules.canAdminister(
                Role.ADMIN, Role.SUPER_ADMIN, Set.of(1L, 2L), Set.of(1L)));
    }

    @Test
    void adminCanAdministerTargetWhoseStoresAreSubsetOfOwn() {
        assertTrue(AdminAuthorizationRules.canAdminister(
                Role.ADMIN, Role.CAMARERO, Set.of(1L, 2L, 3L), Set.of(1L, 2L)));
        assertTrue(AdminAuthorizationRules.canAdminister(
                Role.ADMIN, Role.ADMIN, Set.of(1L, 2L), Set.of(1L, 2L)));
    }

    @Test
    void adminCannotAdministerTargetWithStoreOutsideOwnScope() {
        assertFalse(AdminAuthorizationRules.canAdminister(
                Role.ADMIN, Role.CAMARERO, Set.of(1L, 2L), Set.of(1L, 5L)));
    }

    // ---- isDefaultStoreInActiveSet ---------------------------------------

    @Test
    void defaultStoreMustBelongToActiveSet() {
        assertTrue(AdminAuthorizationRules.isDefaultStoreInActiveSet(1L, Set.of(1L, 2L)));
        assertFalse(AdminAuthorizationRules.isDefaultStoreInActiveSet(3L, Set.of(1L, 2L)));
    }

    @Test
    void defaultStoreIsInvalidWithNullOrEmptyActiveSet() {
        assertFalse(AdminAuthorizationRules.isDefaultStoreInActiveSet(1L, Set.of()));
        assertFalse(AdminAuthorizationRules.isDefaultStoreInActiveSet(1L, null));
        assertFalse(AdminAuthorizationRules.isDefaultStoreInActiveSet(null, Set.of(1L)));
    }

    // ---- canRevokeKeepingAtLeastOne ----------------------------------------

    @Test
    void cannotRevokeLastActiveStore() {
        assertFalse(AdminAuthorizationRules.canRevokeKeepingAtLeastOne(Set.of(1L), 1L));
    }

    @Test
    void canRevokeWhenMoreThanOneActiveStoreRemains() {
        assertTrue(AdminAuthorizationRules.canRevokeKeepingAtLeastOne(Set.of(1L, 2L), 1L));
    }

    @Test
    void revokingStoreNotCurrentlyActiveIsHarmless() {
        assertTrue(AdminAuthorizationRules.canRevokeKeepingAtLeastOne(Set.of(1L, 2L), 99L));
    }

    // ---- isRoleChangeAllowed (escalada de rol) ----------------------------

    @Test
    void superAdminCanAlwaysChangeRoles() {
        assertTrue(AdminAuthorizationRules.isRoleChangeAllowed(
                Role.SUPER_ADMIN, Role.CAMARERO, Role.SUPER_ADMIN));
    }

    @Test
    void camareroActorCanNeverChangeRoles() {
        assertFalse(AdminAuthorizationRules.isRoleChangeAllowed(
                Role.CAMARERO, Role.CAMARERO, Role.ADMIN));
    }

    @Test
    void adminCannotEscalateAnyoneToSuperAdmin() {
        assertFalse(AdminAuthorizationRules.isRoleChangeAllowed(
                Role.ADMIN, Role.CAMARERO, Role.SUPER_ADMIN));
    }

    @Test
    void adminCannotModifyExistingSuperAdmin() {
        assertFalse(AdminAuthorizationRules.isRoleChangeAllowed(
                Role.ADMIN, Role.SUPER_ADMIN, Role.ADMIN));
    }

    @Test
    void adminCanChangeRoleBetweenAdminAndCamarero() {
        assertTrue(AdminAuthorizationRules.isRoleChangeAllowed(
                Role.ADMIN, Role.CAMARERO, Role.ADMIN));
        assertTrue(AdminAuthorizationRules.isRoleChangeAllowed(
                Role.ADMIN, Role.ADMIN, Role.CAMARERO));
    }
}
