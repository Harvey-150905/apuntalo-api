package com.harbeyescala.api_apuntalo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limpieza periódica opcional de registros de idempotencia expirados
 * (Fase 4). Deshabilitada por defecto ({@code app.idempotency.cleanup-enabled=false})
 * para no sorprender a despliegues que no habilitaron {@code @EnableScheduling}
 * a propósito; puede activarse sin cambiar código.
 */
@Component
public class IdempotencyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyRecordService recordService;

    @Value("${app.idempotency.cleanup-enabled:false}")
    private boolean cleanupEnabled;

    public IdempotencyCleanupScheduler(IdempotencyRecordService recordService) {
        this.recordService = recordService;
    }

    @Scheduled(fixedDelayString = "${app.idempotency.cleanup-delay-ms:3600000}")
    public void cleanupExpiredRecords() {
        if (!cleanupEnabled) {
            return;
        }

        int deleted = recordService.deleteExpired();

        if (deleted > 0) {
            log.info("Limpieza de idempotencia: {} registros expirados eliminados", deleted);
        }
    }
}
