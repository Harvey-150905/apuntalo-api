package com.harbeyescala.api_apuntalo.service;

import com.harbeyescala.api_apuntalo.entity.enums.OperationScopeType;

import java.util.Map;

final class IdempotencyOperationScopes {
    private static final Map<String, OperationScopeType> SCOPES = Map.ofEntries(
            Map.entry("TICKET_CREATE", OperationScopeType.STORE),
            Map.entry("TICKET_ADD_LINES", OperationScopeType.STORE),
            Map.entry("TICKET_PAY", OperationScopeType.STORE),
            Map.entry("TICKET_CANCEL_LINE", OperationScopeType.STORE),
            Map.entry("TICKET_CANCEL", OperationScopeType.STORE),
            Map.entry("APPLY_LINE_DISCOUNT", OperationScopeType.STORE),
            Map.entry("TICKET_CANCEL_BATCH", OperationScopeType.STORE),
            Map.entry("TICKET_CHANGE_MESA", OperationScopeType.STORE),
            Map.entry("CASH_SESSION_OPEN", OperationScopeType.STORE),
            Map.entry("CASH_SESSION_CLOSE", OperationScopeType.STORE),
            Map.entry("CASH_MOVEMENT_CREATE", OperationScopeType.STORE),
            Map.entry("CASH_REGISTER_CREATE", OperationScopeType.STORE),
            Map.entry("CASH_REGISTER_UPDATE", OperationScopeType.STORE),
            Map.entry("CASH_REGISTER_STATUS_UPDATE", OperationScopeType.STORE),
            Map.entry("CASH_MANAGEMENT_CONFIG_UPDATE", OperationScopeType.STORE)
    );

    private IdempotencyOperationScopes() {}

    static OperationScopeType require(String operation) {
        OperationScopeType scope = SCOPES.get(operation);
        if (scope == null) {
            throw new IllegalArgumentException("Operación idempotente sin clasificación explícita");
        }
        return scope;
    }
}
