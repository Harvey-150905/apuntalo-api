package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.IdempotencyRecord;
import com.harbeyescala.api_apuntalo.entity.enums.IdempotencyStatus;
import com.harbeyescala.api_apuntalo.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
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

    public IdempotencyRecordService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(Long tenantId, Long userId, String operation, String key) {
        return repository.findByTenantIdAndUserIdAndOperationAndIdempotencyKey(tenantId, userId, operation, key);
    }

    @Transactional
    public IdempotencyRecord begin(
            Long tenantId,
            Long userId,
            String operation,
            String key,
            String requestHash,
            String resourceType,
            Duration retention
    ) {
        IdempotencyRecord record = IdempotencyRecord.builder()
                .tenantId(tenantId)
                .userId(userId)
                .operation(operation)
                .idempotencyKey(key)
                .requestHash(requestHash)
                .resourceType(resourceType)
                .status(IdempotencyStatus.PROCESSING)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plus(retention))
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
            record.setCompletedAt(LocalDateTime.now());
            repository.save(record);
        });
    }

    @Transactional
    public void fail(Long id) {
        repository.findById(id).ifPresent(record -> {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setCompletedAt(LocalDateTime.now());
            repository.save(record);
        });
    }

    @Transactional
    public void discard(Long id) {
        repository.deleteById(id);
    }

    @Transactional
    public int deleteExpired() {
        return repository.deleteExpired(LocalDateTime.now());
    }
}
