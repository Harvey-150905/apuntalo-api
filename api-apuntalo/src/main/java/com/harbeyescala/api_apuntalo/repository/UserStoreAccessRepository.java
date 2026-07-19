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
}
