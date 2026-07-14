package com.harbeyescala.api_apuntalo.exception;

import lombok.Getter;

/**
 * Petición mal formada a nivel de negocio (p.ej. falta Idempotency-Key
 * obligatoria, clave con formato inválido). Se traduce a 400.
 */
@Getter
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }
}
