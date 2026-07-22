package com.harbeyescala.api_apuntalo.entity.enums;

/**
 * Tipo de entidad de negocio sobre la que ocurre un {@link AuditAction}.
 */
public enum AuditEntityType {
    TICKET,
    TICKET_LINE,
    NEGOCIO,
    CASH_REGISTER,
    CASH_SESSION,
    // Fase 9: administración multi-tienda
    STORE,
    USER,
    USER_STORE_ACCESS,
    MESA,
    SUBCATEGORY,
    PRODUCT
}
