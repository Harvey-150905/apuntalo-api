package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.AuditEvent;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Registra fallos funcionales seleccionados (Fase 5.3) en una transacción
 * independiente ({@code REQUIRES_NEW}): la operación de negocio que falló
 * revierte todos sus cambios, pero el evento de auditoría del fallo debe
 * sobrevivir para trazabilidad. Se invoca desde bloques catch de
 * {@code TicketService} sobre excepciones funcionales concretas
 * ({@code BusinessRuleException}, {@code ConflictException}), nunca sobre
 * errores de infraestructura o "no encontrado".
 */
@Service
public class AuditFailureRecorder {

    private final AuditEventService auditEventService;
    private final AuditEventRepository repository;

    public AuditFailureRecorder(AuditEventService auditEventService, AuditEventRepository repository) {
        this.auditEventService = auditEventService;
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            String errorCode,
            Map<String, Object> metadata
    ) {
        AuditEvent event = auditEventService.buildEvent(entityType, entityId, action, null, null, metadata, false, errorCode);
        repository.save(event);
    }
}
