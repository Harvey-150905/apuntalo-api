package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {
    Optional<Store> findByIdAndNegocioId(Long id, Long negocioId);
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

    List<Store> findAllByNegocioIdOrderByNameAscIdAsc(Long negocioId);

    List<Store> findByNegocioIdAndActiveTrueOrderByNameAscIdAsc(Long negocioId);

    boolean existsByNegocioIdAndNormalizedName(Long negocioId, String normalizedName);

    boolean existsByNegocioIdAndNormalizedNameAndIdNot(Long negocioId, String normalizedName, Long id);

    boolean existsByNegocioIdAndCode(Long negocioId, String code);

    boolean existsByNegocioIdAndCodeAndIdNot(Long negocioId, String code, Long id);
}
