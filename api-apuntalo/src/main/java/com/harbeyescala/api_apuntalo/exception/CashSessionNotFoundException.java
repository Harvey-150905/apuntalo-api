package com.harbeyescala.api_apuntalo.exception;

public class CashSessionNotFoundException extends RuntimeException {
    public CashSessionNotFoundException() { super("Sesión de caja no encontrada"); }
}
