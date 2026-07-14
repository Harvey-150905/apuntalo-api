package com.harbeyescala.api_apuntalo.exception;

import lombok.Getter;

@Getter
public class ApiError {
    private final String code;
    private final String message;

    public ApiError(String message) {
        this("ERROR", message);
    }

    public ApiError(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
