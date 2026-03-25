package com.harbeyescala.api_apuntalo.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class ValidationErrorResponse {
    private String message;
    private Map<String, String> errors;
}