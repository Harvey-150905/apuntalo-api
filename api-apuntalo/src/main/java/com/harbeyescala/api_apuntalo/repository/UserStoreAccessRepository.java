package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.UserStoreAccess;
import com.harbeyescala.api_apuntalo.entity.UserStoreAccessId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserStoreAccessRepository extends JpaRepository<UserStoreAccess, UserStoreAccessId> {
    @Query("""
            select access from UserStoreAccess access
            where access.user.id = :userId and access.store.id = :storeId
              and access.negocio.id = :negocioId
            """)
    Optional<UserStoreAccess> findByUserIdAndStoreIdAndNegocioId(
            @Param("userId") Long userId, @Param("storeId") Long storeId,
            @Param("negocioId") Long negocioId);

    @Query("""
            select (count(access) > 0) from UserStoreAccess access
            where access.user.id = :userId and access.store.id = :storeId
              and access.negocio.id = :negocioId and access.active = true
            """)
    boolean existsByUserIdAndStoreIdAndNegocioIdAndActiveTrue(
            @Param("userId") Long userId, @Param("storeId") Long storeId,
            @Param("negocioId") Long negocioId);

    @Query("""
            select access from UserStoreAccess access
            where access.user.id = :userId and access.negocio.id = :negocioId
              and access.active = true
            order by access.store.name asc, access.store.id asc
            """)
    List<UserStoreAccess> findAllByUserIdAndNegocioIdAndActiveTrue(
            @Param("userId") Long userId, @Param("negocioId") Long negocioId);

    @Query("""
            select access from UserStoreAccess access
            where access.store.id = :storeId and access.negocio.id = :negocioId
              and access.active = true
            """)
    List<UserStoreAccess> findAllByStoreIdAndNegocioIdAndActiveTrue(
            @Param("storeId") Long storeId, @Param("negocioId") Long negocioId);

    @Query("""
            select count(access) from UserStoreAccess access
            where access.user.id = :userId and access.negocio.id = :negocioId
              and access.active = true
            """)
    long countByUserIdAndNegocioIdAndActiveTrue(
            @Param("userId") Long userId, @Param("negocioId") Long negocioId);

    @Query("""
            select (count(access) > 0) from UserStoreAccess access
            where access.user.id = :userId and access.negocio.id = :negocioId
            """)
    boolean existsByUserIdAndNegocioId(
            @Param("userId") Long userId, @Param("negocioId") Long negocioId);

    @EntityGraph(attributePaths = "store")
    @Query("""
            select access from UserStoreAccess access
            where access.user.id = :userId
              and access.negocio.id = :negocioId
              and access.active = true
              and access.store.active = true
            order by access.store.name asc, access.store.id asc
            """)
    List<UserStoreAccess> findAuthorizedActiveStores(
            @Param("userId") Long userId, @Param("negocioId") Long negocioId);

    @EntityGraph(attributePaths = "store")
    @Query("""
            select access from UserStoreAccess access
            where access.user.id = :userId and access.store.id = :storeId
              and access.negocio.id = :negocioId and access.active = true
              and access.store.active = true
            """)
    Optional<UserStoreAccess> findValidActiveStoreAccess(
            @Param("userId") Long userId, @Param("storeId") Long storeId,
            @Param("negocioId") Long negocioId);

    // --- Fase 9: administración de accesos ---

    /**
     * Todas las filas de acceso del usuario (activas e inactivas) para poder
     * reactivar en vez de duplicar (F9.5).
     */
    @EntityGraph(attributePaths = "store")
    @Query("""
            select access from UserStoreAccess access
            where access.user.id = :userId and access.negocio.id = :negocioId
            order by access.store.name asc, access.store.id asc
            """)
    List<UserStoreAccess> findAllByUserIdAndNegocioId(
            @Param("userId") Long userId, @Param("negocioId") Long negocioId);

    /**
     * {@code true} si desactivar la Store dejaría a algún usuario activo sin
     * ninguna Store activa autorizada (F9.3). Un usuario queda "varado" si su
     * único acceso activo a una Store activa es el de la Store a desactivar.
     */
    @Query("""
            select (count(u) > 0) from User u
            where u.negocio.id = :negocioId and u.activo = true
              and exists (
                select 1 from UserStoreAccess a
                where a.user.id = u.id and a.store.id = :storeId
                  and a.negocio.id = :negocioId and a.active = true)
              and not exists (
                select 1 from UserStoreAccess a2
                where a2.user.id = u.id and a2.negocio.id = :negocioId
                  and a2.active = true and a2.store.id <> :storeId
                  and a2.store.active = true)
            """)
    boolean existsActiveUserStrandedByStoreDisable(
            @Param("negocioId") Long negocioId, @Param("storeId") Long storeId);
}
