package com.harbeyescala.api_apuntalo.entity.enums;

/**
 * Acciones funcionales auditables de la Fase 5.3. Cada valor corresponde a
 * un evento de negocio significativo sobre tickets o líneas de ticket.
 */
public enum AuditAction {
    TICKET_CREATED,
    TICKET_LINES_ADDED,
    LINE_DISCOUNT_APPLIED,
    LINE_DISCOUNT_REMOVED,
    TICKET_LINE_CANCELLED,
    TICKET_BATCH_CANCELLED,
    TICKET_CANCELLED,
    TICKET_TABLE_CHANGED,
    TICKET_PAID,
    COMMERCIAL_NUMBER_ASSIGNED
}
