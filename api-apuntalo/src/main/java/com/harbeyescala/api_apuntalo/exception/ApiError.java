package com.harbeyescala.api_apuntalo.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    private final Instant timestamp;
    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final List<ApiFieldError> fieldErrors;
    private final Map<String, Object> metadata;

    public ApiError(int status, String code, String message, String path) {
        this(status, code, message, path, null, null);
    }

    public ApiError(int status, String code, String message, String path,
                    List<ApiFieldError> fieldErrors, Map<String, Object> metadata) {
        this.timestamp = Instant.now();
        this.status = status;
        this.code = code;
        this.message = message;
        this.path = path;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
        this.metadata = metadata == null || metadata.isEmpty() ? null : Map.copyOf(metadata);
    }
}
