package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByIdAndNegocioId(Long id, Long negocioId);

    /**
     * Listado administrativo paginado y filtrado (Fase 9, F9.3). Filtros
     * opcionales por estado ({@code active}) y por texto ({@code q}) sobre
     * nombre o código.
     */
    @Query("""
            select s from Store s
            where s.negocioId = :negocioId
              and (:active is null or s.active = :active)
              and (:q is null or lower(s.name) like lower(concat('%', cast(:q as string), '%'))
                   or upper(s.code) like upper(concat('%', cast(:q as string), '%')))
            order by s.name asc, s.id asc
            """)
    Page<Store> searchAdmin(
            @Param("negocioId") Long negocioId,
            @Param("active") Boolean active,
            @Param("q") String q,
            Pageable pageable);

    /**
     * Variante de {@link #searchAdmin} restringida a las Stores con acceso
     * activo del usuario indicado: un ADMIN solo administra sus Stores
     * autorizadas (Fase 9, F9.3).
     */
    @Query("""
            select s from Store s
            where s.negocioId = :negocioId
              and (:active is null or s.active = :active)
              and (:q is null or lower(s.name) like lower(concat('%', cast(:q as string), '%'))
                   or upper(s.code) like upper(concat('%', cast(:q as string), '%')))
              and exists (
                select 1 from UserStoreAccess a
                where a.store.id = s.id and a.user.id = :userId
                  and a.negocio.id = :negocioId and a.active = true)
            order by s.name asc, s.id asc
            """)
    Page<Store> searchAdminAuthorized(
            @Param("negocioId") Long negocioId,
            @Param("active") Boolean active,
            @Param("q") String q,
            @Param("userId") Long userId,
            Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from Store s
            where s.id = :storeId
              and s.negocioId = :negocioId
            """)
    Optional<Store> findByIdAndNegocioIdForUpdate(
            @Param("storeId") Long storeId,
            @Param("negocioId") Long negocioId);

    Optional<Store> findByNegocioIdAndPrimaryStoreTrue(Long negocioId);

    List<Store> findByIdInAndNegocioId(java.util.Collection<Long> ids, Long negocioId);

    List<Store> findAllByNegocioIdOrderByNameAscIdAsc(Long negocioId);

    List<Store> findByNegocioIdAndActiveTrueOrderByNameAscIdAsc(Long negocioId);

    boolean existsByNegocioIdAndNormalizedName(Long negocioId, String normalizedName);

    boolean existsByNegocioIdAndNormalizedNameAndIdNot(Long negocioId, String normalizedName, Long id);

    boolean existsByNegocioIdAndCode(Long negocioId, String code);

    boolean existsByNegocioIdAndCodeAndIdNot(Long negocioId, String code, Long id);
}
