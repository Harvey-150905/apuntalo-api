package com.harbeyescala.api_apuntalo.exception;

import lombok.Getter;

/**
 * Violación de una regla de negocio del dominio (p.ej. intentar pagar un
 * ticket vacío, modificar un ticket no abierto). Se traduce a 422.
 */
@Getter
public class BusinessRuleException extends RuntimeException implements FunctionalException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }
}
