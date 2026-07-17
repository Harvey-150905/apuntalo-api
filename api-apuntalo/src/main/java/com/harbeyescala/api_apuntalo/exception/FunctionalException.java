package com.harbeyescala.api_apuntalo.exception;

/**
 * Marca las excepciones de dominio con código estable ({@link BusinessRuleException},
 * {@link ConflictException}) para poder capturarlas juntas en un multi-catch
 * y seguir accediendo a {@code getCode()} (Fase 5.3: auditoría de fallos
 * funcionales seleccionados en {@code TicketService}).
 */
public interface FunctionalException {
    String getCode();
}
