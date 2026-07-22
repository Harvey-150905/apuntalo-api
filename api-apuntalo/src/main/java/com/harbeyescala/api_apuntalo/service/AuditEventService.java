package com.harbeyescala.api_apuntalo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harbeyescala.api_apuntalo.dto.AuditEventResponseDto;
import com.harbeyescala.api_apuntalo.dto.PageResponseDto;
import com.harbeyescala.api_apuntalo.entity.AuditEvent;
import com.harbeyescala.api_apuntalo.entity.enums.AuditAction;
import com.harbeyescala.api_apuntalo.entity.enums.AuditEntityType;
import com.harbeyescala.api_apuntalo.entity.enums.OperationScopeType;
import com.harbeyescala.api_apuntalo.repository.AuditEventRepository;
import com.harbeyescala.api_apuntalo.security.AuditRequestContext;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
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
    private final Clock clock;
    private final ActiveStoreContext storeContext;

    public AuditEventService(
            AuditEventRepository repository,
            AuditRequestContext requestContext,
            CurrentUser currentUser,
            ObjectMapper objectMapper,
            Clock clock,
            ActiveStoreContext storeContext
    ) {
        this.repository = repository;
        this.requestContext = requestContext;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.storeContext = storeContext;
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
     * Registra un evento de éxito con {@code tenantId}/{@code storeId}
     * explícitos, sin derivarlos del principal autenticado (Fase 9). Es
     * imprescindible para el provisionamiento de plataforma, donde el tenant
     * del JWT (el negocio del SUPER_ADMIN) es distinto del tenant recién
     * creado, y para las operaciones de administración cross-tenant de
     * SUPER_ADMIN sobre otro negocio.
     *
     * <p>El actor ({@code user_id}) sigue siendo el usuario autenticado, que
     * puede ser un SUPER_ADMIN de plataforma perteneciente a otro negocio: la
     * FK {@code fk_audit_events_user} referencia únicamente {@code users(id)},
     * por lo que es válido. Para acciones {@link OperationScopeType#TENANT} el
     * {@code storeId} se ignora (se guarda {@code null}); para acciones
     * {@link OperationScopeType#STORE} el {@code storeId} debe pertenecer al
     * {@code tenantId} indicado (FK compuesta tenant-safe).
     */
    @Transactional
    public void recordSuccessForTenant(
            Long tenantId,
            Long storeId,
            AuditEntityType entityType,
            Long entityId,
            AuditAction action,
            Map<String, Object> previousState,
            Map<String, Object> newState,
            Map<String, Object> metadata
    ) {
        OperationScopeType scopeType = action.scopeType();
        Long effectiveStoreId = scopeType == OperationScopeType.STORE ? storeId : null;
        AuditEvent event = AuditEvent.builder()
                .negocioId(tenantId)
                .storeId(effectiveStoreId)
                .scopeType(scopeType)
                .storeScopeLegacy(false)
                .userId(safeUserId())
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .previousStateJson(toSafeJson(previousState))
                .newStateJson(toSafeJson(newState))
                .occurredAt(LocalDateTime.now(clock))
                .idempotencyKey(requestContext.getIdempotencyKey())
                .requestId(requestContext.getRequestId())
                .success(true)
                .errorCode(null)
                .metadataJson(toSafeJson(metadata))
                .build();
        repository.save(event);
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
            Long userId,
            Boolean success,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        PaginationPolicy.validate(page, size);

        Long tenantId = currentUser.getTenantId();
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));

        Specification<AuditEvent> specification = (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("negocioId"), tenantId);
        Long activeStoreId = storeContext.requireStore().getId();
        specification = specification.and((root, query, cb) -> cb.or(
                cb.equal(root.get("storeId"), activeStoreId), cb.isNull(root.get("storeId"))));

        if (entityType != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("entityType"), entityType));
        }
        if (entityId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (action != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (userId != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (success != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("success"), success));
        }
        if (from != null) {
            specification = specification.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            specification = specification.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }

        Page<AuditEvent> result = repository.findAll(specification, pageable);

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
                .storeId(event.getStoreId())
                .storeScoped(event.getScopeType() == OperationScopeType.STORE)
                .legacyScope(event.getStoreScopeLegacy())
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
        OperationScopeType scopeType = action.scopeType();
        Long storeId = scopeType == OperationScopeType.STORE ? storeContext.requireStore().getId() : null;

        return AuditEvent.builder()
                .negocioId(tenantId)
                .storeId(storeId)
                .scopeType(scopeType)
                .storeScopeLegacy(false)
                .userId(safeUserId())
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .previousStateJson(toSafeJson(previousState))
                .newStateJson(toSafeJson(newState))
                .occurredAt(LocalDateTime.now(clock))
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
