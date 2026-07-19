package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {
    Optional<CashRegister> findByIdAndNegocioId(Long id, Long negocioId);
    Optional<CashRegister> findByIdAndNegocioIdAndStoreId(Long id, Long negocioId, Long storeId);
    List<CashRegister> findByNegocioIdAndStoreIdOrderByNormalizedNameAscIdAsc(Long negocioId,Long storeId);
    List<CashRegister> findByNegocioIdAndStoreIdAndActiveTrueOrderByNormalizedNameAscIdAsc(Long negocioId,Long storeId);
    boolean existsByNegocioIdAndStoreIdAndNormalizedName(Long negocioId,Long storeId,String normalizedName);
    boolean existsByNegocioIdAndStoreIdAndNormalizedNameAndIdNot(Long negocioId,Long storeId,String normalizedName,Long id);
    List<CashRegister> findByNegocioIdOrderByNormalizedNameAscIdAsc(Long negocioId);
    List<CashRegister> findByNegocioIdAndActiveTrueOrderByNormalizedNameAscIdAsc(Long negocioId);
    boolean existsByNegocioIdAndNormalizedName(Long negocioId, String normalizedName);
    boolean existsByNegocioIdAndNormalizedNameAndIdNot(Long negocioId, String normalizedName, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CashRegister r where r.id = :id and r.negocio.id = :negocioId")
    Optional<CashRegister> findByIdAndNegocioIdForUpdate(
            @Param("id") Long id, @Param("negocioId") Long negocioId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CashRegister r where r.id=:id and r.negocio.id=:negocioId and r.store.id=:storeId")
    Optional<CashRegister> findByIdAndNegocioIdAndStoreIdForUpdate(@Param("id")Long id,
      @Param("negocioId")Long negocioId,@Param("storeId")Long storeId);
}
