package com.harbeyescala.api_apuntalo.entity.enums;

/**
 * Acciones funcionales auditables. La Fase 5.3 introdujo las acciones de
 * tickets, cajas y sesiones; la Fase 9 añade las acciones de administración
 * multi-tienda (provisionamiento de negocio, ciclo de vida de Stores,
 * usuarios, accesos y catálogo).
 *
 * El {@link OperationScopeType} determina si el evento se almacena con
 * {@code store_id} (STORE) o solo a nivel de tenant (TENANT). Para
 * operaciones de usuario/negocio que no tienen una única Store se usa
 * TENANT (spec 14.6): una desactivación de usuario multi-tienda no puede
 * atribuirse a una sola Store.
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
    CASH_MOVEMENT_OUT_CREATED(OperationScopeType.STORE), CASH_SESSION_CLOSED(OperationScopeType.STORE),

    // --- Fase 9: administración multi-tienda ---
    // Provisionamiento y ciclo de vida del negocio (nivel plataforma/tenant)
    TENANT_PROVISIONED(OperationScopeType.TENANT),
    NEGOCIO_ACTIVATED(OperationScopeType.TENANT),
    NEGOCIO_DEACTIVATED(OperationScopeType.TENANT),

    // Ciclo de vida de Stores (siempre acotado a la Store afectada)
    STORE_CREATED(OperationScopeType.STORE),
    STORE_UPDATED(OperationScopeType.STORE),
    STORE_ACTIVATED(OperationScopeType.STORE),
    STORE_DEACTIVATED(OperationScopeType.STORE),

    // Usuarios: sin una única Store, se auditan a nivel de tenant
    USER_CREATED(OperationScopeType.TENANT),
    USER_UPDATED(OperationScopeType.TENANT),
    USER_ACTIVATED(OperationScopeType.TENANT),
    USER_DEACTIVATED(OperationScopeType.TENANT),
    USER_ROLE_CHANGED(OperationScopeType.TENANT),
    USER_PASSWORD_RESET(OperationScopeType.TENANT),

    // Accesos usuario-tienda: la concesión/retirada es de una Store concreta;
    // la actualización por lote y el cambio de default son a nivel de tenant.
    USER_STORE_ACCESS_GRANTED(OperationScopeType.STORE),
    USER_STORE_ACCESS_REVOKED(OperationScopeType.STORE),
    USER_STORE_ACCESS_UPDATED(OperationScopeType.TENANT),
    USER_DEFAULT_STORE_CHANGED(OperationScopeType.TENANT),

    // Catálogo y mesas: acotados a la Store activa
    MESA_CREATED(OperationScopeType.STORE),
    MESA_UPDATED(OperationScopeType.STORE),
    MESA_ACTIVATED(OperationScopeType.STORE),
    MESA_DEACTIVATED(OperationScopeType.STORE),
    SUBCATEGORY_CREATED(OperationScopeType.STORE),
    SUBCATEGORY_UPDATED(OperationScopeType.STORE),
    SUBCATEGORY_ACTIVATED(OperationScopeType.STORE),
    SUBCATEGORY_DEACTIVATED(OperationScopeType.STORE),
    PRODUCT_CREATED(OperationScopeType.STORE),
    PRODUCT_UPDATED(OperationScopeType.STORE),
    PRODUCT_ACTIVATED(OperationScopeType.STORE),
    PRODUCT_DEACTIVATED(OperationScopeType.STORE);

    private final OperationScopeType scopeType;
    AuditAction(OperationScopeType scopeType) { this.scopeType = scopeType; }
    public OperationScopeType scopeType() { return scopeType; }
}
