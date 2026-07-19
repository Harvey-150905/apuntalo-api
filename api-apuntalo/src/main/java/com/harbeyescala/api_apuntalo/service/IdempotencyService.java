package com.harbeyescala.api_apuntalo.service;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.harbeyescala.api_apuntalo.dto.IdempotentOutcome;
import com.harbeyescala.api_apuntalo.entity.IdempotencyRecord;
import com.harbeyescala.api_apuntalo.entity.enums.IdempotencyStatus;
import com.harbeyescala.api_apuntalo.entity.enums.OperationScopeType;
import com.harbeyescala.api_apuntalo.exception.BadRequestException;
import com.harbeyescala.api_apuntalo.exception.ConflictException;
import com.harbeyescala.api_apuntalo.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Orquesta el flujo de idempotencia descrito en la Fase 4: valida la
 * cabecera Idempotency-Key, calcula un hash estable de la petición,
 * reutiliza la respuesta guardada si la clave ya se procesó con el mismo
 * contenido, y traduce en conflicto (409) las reutilizaciones con
 * contenido distinto o las ejecuciones simultáneas de la misma clave.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordService recordService;
    private final CurrentUser currentUser;
    private final ObjectMapper hashMapper;
    private final ObjectMapper jsonMapper;
    private final ActiveStoreContext storeContext;

    @Value("${app.idempotency.enforced:true}")
    private boolean enforced;

    @Value("${app.idempotency.max-key-length:100}")
    private int maxKeyLength;

    @Value("${app.idempotency.retention-hours:48}")
    private long retentionHours;

    @Value("${app.idempotency.processing-timeout-minutes:5}")
    private long processingTimeoutMinutes;

    public IdempotencyService(IdempotencyRecordService recordService, CurrentUser currentUser, ObjectMapper objectMapper,
                              ActiveStoreContext storeContext) {
        this.recordService = recordService;
        this.currentUser = currentUser;
        this.jsonMapper = objectMapper;
        this.hashMapper = objectMapper.copy();
        this.storeContext = storeContext;
        this.hashMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.hashMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public <T> IdempotentOutcome<T> execute(
            String operation,
            String resourceType,
            String idempotencyKey,
            Object requestPayload,
            int successStatus,
            Class<T> responseType,
            Supplier<T> action
    ) {
        return execute(operation, resourceType, idempotencyKey, requestPayload, successStatus, responseType, action, false);
    }

    private <T> IdempotentOutcome<T> execute(
            String operation,
            String resourceType,
            String idempotencyKey,
            Object requestPayload,
            int successStatus,
            Class<T> responseType,
            Supplier<T> action,
            boolean isRetryAfterFailure
    ) {
        String key = validateKey(idempotencyKey);

        if (key == null) {
            // Idempotencia no exigida en este entorno y no se envió cabecera: ejecutar sin protección.
            return new IdempotentOutcome<>(action.get(), false, successStatus);
        }

        Long tenantId = currentUser.getTenantId();
        Long userId = currentUser.getUserId();
        OperationScopeType scopeType = IdempotencyOperationScopes.require(operation);
        Long storeId = scopeType == OperationScopeType.STORE ? storeContext.requireStore().getId() : null;
        String requestHash = computeHash(java.util.Map.of(
                "storeId", storeId == null ? "TENANT" : storeId,
                "payload", requestPayload == null ? new Object() : requestPayload));

        Optional<IdempotencyRecord> existing = recordService.find(tenantId, userId, storeId, operation, key);

        if (existing.isPresent()) {
            IdempotencyRecord existingRecord = existing.get();
            if (existingRecord.getStatus() == IdempotencyStatus.PROCESSING
                    && existingRecord.getRequestHash().equals(requestHash)
                    && recordService.discardStaleProcessing(
                            tenantId, userId, storeId, operation, key, requestHash,
                            Duration.ofMinutes(processingTimeoutMinutes))) {
                return execute(operation, resourceType, key, requestPayload,
                        successStatus, responseType, action, true);
            }
            Optional<IdempotentOutcome<T>> replay = handleExisting(existingRecord, requestHash, responseType);
            if (replay.isPresent()) {
                return replay.get();
            }
            if (isRetryAfterFailure) {
                throw new ConflictException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                        "Ya existe una operación en curso para esta Idempotency-Key");
            }
            // Registro anterior en FAILED: se descartó dentro de handleExisting, reintentar una vez.
            return execute(operation, resourceType, key, requestPayload, successStatus, responseType, action, true);
        }

        IdempotencyRecord record;
        try {
            record = recordService.begin(tenantId, userId, storeId, scopeType, operation, key, requestHash, resourceType, Duration.ofHours(retentionHours));
        } catch (DataIntegrityViolationException race) {
            IdempotencyRecord raced = recordService.find(tenantId, userId, storeId, operation, key)
                    .orElseThrow(() -> new ConflictException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                            "Ya existe una operación en curso para esta Idempotency-Key"));

            return handleExisting(raced, requestHash, responseType)
                    .orElseThrow(() -> new ConflictException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                            "Ya existe una operación en curso para esta Idempotency-Key"));
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException ex) {
            recordService.fail(record.getId());
            throw ex;
        }

        recordService.complete(record.getId(), successStatus, serialize(result), extractResourceId(result));

        return new IdempotentOutcome<>(result, false, successStatus);
    }

    /**
     * @return el outcome de reproducción si el registro es reutilizable
     * (COMPLETED con mismo hash); vacío si el registro estaba FAILED y ya
     * fue descartado para permitir un reintento limpio.
     */
    private <T> Optional<IdempotentOutcome<T>> handleExisting(
            IdempotencyRecord record,
            String requestHash,
            Class<T> responseType
    ) {
        if (record.getStatus() == IdempotencyStatus.FAILED) {
            recordService.discard(record.getId());
            return Optional.empty();
        }

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST",
                    "La Idempotency-Key ya se usó con una petición de contenido distinto");
        }

        if (record.getStatus() == IdempotencyStatus.PROCESSING) {
            throw new ConflictException("IDEMPOTENCY_REQUEST_IN_PROGRESS",
                    "La operación para esta Idempotency-Key todavía se está procesando");
        }

        // COMPLETED con el mismo hash: se reproduce la respuesta original.
        T body = deserialize(record.getResponseBody(), responseType);
        int status = record.getResponseStatus() != null ? record.getResponseStatus() : 200;
        return Optional.of(new IdempotentOutcome<>(body, true, status));
    }

    private String validateKey(String idempotencyKey) {
        boolean blank = idempotencyKey == null || idempotencyKey.isBlank();

        if (blank) {
            if (enforced) {
                throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "La cabecera Idempotency-Key es obligatoria");
            }
            return null;
        }

        if (idempotencyKey.length() > maxKeyLength) {
            throw new BadRequestException("IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key no puede superar " + maxKeyLength + " caracteres");
        }

        return idempotencyKey.trim();
    }

    private String computeHash(Object payload) {
        try {
            JsonNode tree = hashMapper.valueToTree(payload == null ? new Object() : payload);
            String canonicalJson = hashMapper.writeValueAsString(canonicalizeNumbers(tree));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la petición", e);
        }
    }

    private JsonNode canonicalizeNumbers(JsonNode node) {
        if (node == null) return null;
        if (node.isBigDecimal()) {
            return DecimalNode.valueOf(MoneyPolicy.canonicalize(node.decimalValue()));
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.fields().forEachRemaining(entry -> object.set(entry.getKey(), canonicalizeNumbers(entry.getValue())));
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, canonicalizeNumbers(array.get(index)));
            }
        }
        return node;
    }

    private String serialize(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la respuesta idempotente", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return jsonMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo reproducir la respuesta idempotente", e);
        }
    }

    private Long extractResourceId(Object result) {
        try {
            var method = result.getClass().getMethod("getId");
            Object value = method.invoke(result);
            return value instanceof Long l ? l : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
