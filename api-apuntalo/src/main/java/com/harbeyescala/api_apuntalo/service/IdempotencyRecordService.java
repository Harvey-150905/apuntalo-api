package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.IdempotencyRecord;
import com.harbeyescala.api_apuntalo.entity.enums.IdempotencyStatus;
import com.harbeyescala.api_apuntalo.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.Optional;

/**
 * Operaciones atómicas de bajo nivel sobre {@link IdempotencyRecord}.
 * Se mantiene como bean independiente (no llamado desde otro método del
 * Participan en la misma transacción que la operación de negocio para que
 * negocio y respuesta idempotente se confirmen o reviertan juntos.
 */
@Service
public class IdempotencyRecordService {

    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    public IdempotencyRecordService(IdempotencyRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(Long tenantId, Long userId, Long storeId, String operation, String key) {
        return repository.findByTenantIdAndUserIdAndStoreIdAndOperationAndIdempotencyKey(tenantId, userId, storeId, operation, key);
    }

    @Transactional
    public IdempotencyRecord begin(
            Long tenantId,
            Long userId,
            Long storeId,
            com.harbeyescala.api_apuntalo.entity.enums.OperationScopeType scopeType,
            String operation,
            String key,
            String requestHash,
            String resourceType,
            Duration retention
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        IdempotencyRecord record = IdempotencyRecord.builder()
                .tenantId(tenantId)
                .userId(userId)
                .storeId(storeId)
                .scopeType(scopeType)
                .storeScopeLegacy(false)
                .operation(operation)
                .idempotencyKey(key)
                .requestHash(requestHash)
                .resourceType(resourceType)
                .status(IdempotencyStatus.PROCESSING)
                .createdAt(now)
                .expiresAt(now.plus(retention))
                .build();

        return repository.saveAndFlush(record);
    }

    @Transactional
    public void complete(Long id, Integer responseStatus, String responseBody, Long resourceId) {
        repository.findById(id).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setResponseStatus(responseStatus);
            record.setResponseBody(responseBody);
            record.setResourceId(resourceId);
            record.setCompletedAt(LocalDateTime.now(clock));
            repository.save(record);
        });
    }

    @Transactional
    public void fail(Long id) {
        repository.findById(id).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setCompletedAt(LocalDateTime.now(clock));
            repository.save(record);
        });
    }

    @Transactional
    public void discard(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public boolean discardStaleProcessing(
            Long tenantId, Long userId, Long storeId, String operation, String key,
            String requestHash, Duration timeout) {
        Optional<IdempotencyRecord> locked = repository.findScopedForUpdate(tenantId, userId, storeId, operation, key);
        if (locked.isEmpty()) {
            return true;
        }
        IdempotencyRecord record = locked.get();
        boolean stale = record.getStatus() == IdempotencyStatus.PROCESSING
                && record.getRequestHash().equals(requestHash)
                && record.getCreatedAt().isBefore(LocalDateTime.now(clock).minus(timeout));
        if (!stale) {
            return false;
        }
        repository.delete(record);
        repository.flush();
        return true;
    }

    @Transactional
    public int deleteExpired() {
        return repository.deleteExpired(LocalDateTime.now(clock));
    }
}
