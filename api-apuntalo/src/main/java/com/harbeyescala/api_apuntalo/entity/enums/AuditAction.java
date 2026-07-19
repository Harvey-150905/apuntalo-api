package com.harbeyescala.api_apuntalo.entity.enums;

/**
 * Acciones funcionales auditables de la Fase 5.3. Cada valor corresponde a
 * un evento de negocio significativo sobre tickets o líneas de ticket.
 */
public enum AuditAction {
    TICKET_CREATED(OperationScopeType.STORE),
    TICKET_LINES_ADDED(OperationScopeType.STORE), LINE_DISCOUNT_APPLIED(OperationScopeType.STORE),
    LINE_DISCOUNT_REMOVED(OperationScopeType.STORE), TICKET_LINE_CANCELLED(OperationScopeType.STORE),
    TICKET_BATCH_CANCELLED(OperationScopeType.STORE), TICKET_CANCELLED(OperationScopeType.STORE),
    TICKET_TABLE_CHANGED(OperationScopeType.STORE), TICKET_PAID(OperationScopeType.STORE),
    COMMERCIAL_NUMBER_ASSIGNED(OperationScopeType.STORE),
    CASH_MANAGEMENT_ENABLED(OperationScopeType.TENANT), CASH_MANAGEMENT_DISABLED(OperationScopeType.TENANT),
    CASH_REGISTER_CREATED(OperationScopeType.STORE), CASH_REGISTER_RENAMED(OperationScopeType.STORE),
    CASH_REGISTER_ACTIVATED(OperationScopeType.STORE), CASH_REGISTER_DEACTIVATED(OperationScopeType.STORE),
    CASH_SESSION_OPENED(OperationScopeType.STORE), CASH_RECONCILIATION_ENABLED(OperationScopeType.STORE),
    CASH_RECONCILIATION_DISABLED(OperationScopeType.STORE), CASH_MOVEMENT_IN_CREATED(OperationScopeType.STORE),
    CASH_MOVEMENT_OUT_CREATED(OperationScopeType.STORE), CASH_SESSION_CLOSED(OperationScopeType.STORE);

    private final OperationScopeType scopeType;
    AuditAction(OperationScopeType scopeType) { this.scopeType = scopeType; }
    public OperationScopeType scopeType() { return scopeType; }
}
