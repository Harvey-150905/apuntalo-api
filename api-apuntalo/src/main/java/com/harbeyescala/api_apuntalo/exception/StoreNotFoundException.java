package com.harbeyescala.api_apuntalo.exception;

public class StoreNotFoundException extends RuntimeException {
    public StoreNotFoundException() {
        super("Tienda no encontrada");
    }
}
