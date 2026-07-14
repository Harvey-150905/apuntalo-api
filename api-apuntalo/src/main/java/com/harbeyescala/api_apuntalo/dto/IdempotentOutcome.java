package com.harbeyescala.api_apuntalo.dto;

/**
 * Resultado de ejecutar una operación a través de {@code IdempotencyService}.
 * {@code replayed} indica si la respuesta viene de un registro previo
 * (COMPLETED) en lugar de haberse ejecutado la lógica de negocio de nuevo.
 */
public record IdempotentOutcome<T>(T body, boolean replayed, int status) {
}
