package com.harbeyescala.api_apuntalo.exception;

public class CashRegisterNotFoundException extends RuntimeException {
    public CashRegisterNotFoundException() {
        super("Caja no encontrada");
    }
}
