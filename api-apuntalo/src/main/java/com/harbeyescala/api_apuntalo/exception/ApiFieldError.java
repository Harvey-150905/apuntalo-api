package com.harbeyescala.api_apuntalo.exception;

public record ApiFieldError(String field, String code, String message) {
}
