package com.harbeyescala.api_apuntalo.exception;

import lombok.Getter;

/**
 * Conflicto de estado o de concurrencia (mesa ya ocupada, ticket modificado
 * por otra petición, idempotency-key en uso). Se traduce a 409.
 */
@Getter
public class ConflictException extends RuntimeException implements FunctionalException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
