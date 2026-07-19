package com.harbeyescala.api_apuntalo.repository;
import com.harbeyescala.api_apuntalo.entity.CashMovement;
import com.harbeyescala.api_apuntalo.entity.enums.CashMovementType;
import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
public interface CashMovementRepository extends JpaRepository<CashMovement,Long> {
 @EntityGraph(attributePaths={"performedBy"})
 Page<CashMovement> findByCashSessionIdAndNegocioId(Long sessionId, Long negocioId, Pageable pageable);
 Page<CashMovement> findByCashSessionIdAndNegocioIdAndStoreId(Long sessionId,Long negocioId,Long storeId,Pageable pageable);
 @Query("select coalesce(sum(m.amount),0) from CashMovement m where m.cashSession.id=:sessionId and m.negocio.id=:tenantId and m.store.id=:storeId and m.type=:type")
 BigDecimal sumAmount(@Param("sessionId") Long sessionId,@Param("tenantId") Long tenantId,@Param("storeId")Long storeId,@Param("type") CashMovementType type);
}
