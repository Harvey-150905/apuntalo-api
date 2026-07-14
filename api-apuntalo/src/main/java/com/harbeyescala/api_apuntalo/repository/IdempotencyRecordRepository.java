package com.harbeyescala.api_apuntalo.repository;

import com.harbeyescala.api_apuntalo.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
            Long tenantId,
            Long userId,
            String operation,
            String idempotencyKey
    );

    @Modifying
    @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);
}
