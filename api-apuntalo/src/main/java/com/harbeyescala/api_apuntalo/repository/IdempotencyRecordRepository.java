package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByTenantIdAndUserIdAndStoreIdAndOperationAndIdempotencyKey(
            Long tenantId,
            Long userId,
            Long storeId,
            String operation,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select r from IdempotencyRecord r
        where r.tenantId = :tenantId and r.userId = :userId
          and ((:storeId is null and r.storeId is null) or r.storeId = :storeId)
          and r.operation = :operation and r.idempotencyKey = :key
    """)
    Optional<IdempotencyRecord> findScopedForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("storeId") Long storeId,
            @Param("operation") String operation,
            @Param("key") String key);

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
