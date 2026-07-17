package com.harbeyescala.api_apuntalo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbeyescala.api_apuntalo.dto.AuditEventResponseDto;
import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.entity.AuditEvent;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.repository.AuditEventRepository;
import com.harbeyescala.api_apuntalo.security.AuditRequestContext;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auditoría funcional segura (Fase 5.3). Los eventos de éxito se registran
 * en la misma transacción que la operación de negocio: si esta se revierte,
 * el evento se revierte con ella (no tiene sentido auditar un cambio que
 * nunca ocurrió). Los fallos usan {@link AuditFailureRecorder} en una
 * transacción independiente para sobrevivir al rollback de la operación
 * fallida.
 *
 * Los snapshots solo aceptan {@code Map<String, Object>} construidos a
 * mano por el llamador (nunca entidades de Hibernate), y aun así se
 * sanean para eliminar cualquier clave que pueda contener credenciales.
 */
@Service
public class AuditEventService {

    private static final Set<String> FORBIDDEN_KEY_FRAGMENTS = Set.of(
            "password", "secret", "token", "authorization", "jwt", "credential"
    );

    private final AuditEventRepository repository;
    private final AuditRequestContext requestContext;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public AuditEventService(
            AuditEventRepository repository,
            AuditRequestContext requestContext,
            CurrentUser currentUser,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.requestContext = requestContext;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordSuccess(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Map<String, Object> previousState,
            Map<String, Object> newState
    ) {
        recordSuccess(entityType, entityId, action, previousState, newState, null);
    }

    @Transactional
    public void recordSuccess(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Map<String, Object> previousState,
            Map<String, Object> newState,
            Map<String, Object> metadata
    ) {
        repository.save(buildEvent(entityType, entityId, action, previousState, newState, metadata, true, null));
    }

    /**
     * Listado filtrado y paginado para {@code GET /api/admin/audit-events}
     * (Fase 5.3), siempre acotado al tenant del usuario autenticado.
     */
    @Transactional(readOnly = true)
    public PageResponseDto<AuditEventResponseDto> findFiltered(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Boolean success,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        Long tenantId = currentUser.getTenantId();
        Pageable pageable = PageRequest.of(page, size);

        Page<AuditEvent> result = repository.findFiltered(
                tenantId, entityType, entityId, action, success, from, to, pageable
        );

        List<AuditEventResponseDto> content = result.getContent().stream()
                .map(this::toResponseDto)
                .toList();

        return new PageResponseDto<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isLast()
        );
    }

    private AuditEventResponseDto toResponseDto(AuditEvent event) {
        return AuditEventResponseDto.builder()
                .id(event.getId())
                .negocioId(event.getNegocioId())
                .userId(event.getUserId())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .action(event.getAction())
                .previousStateJson(event.getPreviousStateJson())
                .newStateJson(event.getNewStateJson())
                .occurredAt(event.getOccurredAt())
                .idempotencyKey(event.getIdempotencyKey())
                .requestId(event.getRequestId())
                .success(event.getSuccess())
                .errorCode(event.getErrorCode())
                .metadataJson(event.getMetadataJson())
                .build();
    }

    /**
     * Construye (sin persistir) el evento de auditoría. Método usado tanto
     * aquí como por {@link AuditFailureRecorder}, que lo persiste en su
     * propia transacción {@code REQUIRES_NEW}.
     */
    AuditEvent buildEvent(
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Map<String, Object> previousState,
            Map<String, Object> newState,
            Map<String, Object> metadata,
            boolean success,
            String errorCode
    ) {
        Long tenantId = currentUser.getTenantId();

        return AuditEvent.builder()
                .negocioId(tenantId)
                .userId(safeUserId())
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .previousStateJson(toSafeJson(previousState))
                .newStateJson(toSafeJson(newState))
                .occurredAt(LocalDateTime.now())
                .idempotencyKey(requestContext.getIdempotencyKey())
                .requestId(requestContext.getRequestId())
                .success(success)
                .errorCode(errorCode)
                .metadataJson(toSafeJson(metadata))
                .build();
    }

    private Long safeUserId() {
        try {
            return currentUser.getUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String toSafeJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(sanitize(data));
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();

        data.forEach((key, value) -> {
            if (isForbiddenKey(key)) {
                return;
            }

            if (value instanceof Map<?, ?> nestedMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) nestedMap;
                result.put(key, sanitize(nested));
            } else {
                result.put(key, value);
            }
        });

        return result;
    }

    private boolean isForbiddenKey(String key) {
        if (key == null) {
            return false;
        }

        String normalized = key.toLowerCase();
        return FORBIDDEN_KEY_FRAGMENTS.stream().anyMatch(normalized::contains);
    }
}
