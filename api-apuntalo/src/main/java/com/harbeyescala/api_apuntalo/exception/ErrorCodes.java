package com.harbeyescala.api_apuntalo.exception;

public final class ErrorCodes {
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String INVALID_REQUEST_BODY = "INVALID_REQUEST_BODY";
    public static final String INVALID_FIELD_TYPE = "INVALID_FIELD_TYPE";
    public static final String INVALID_ENUM_VALUE = "INVALID_ENUM_VALUE";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String INVALID_FILE = "INVALID_FILE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String RESOURCE_CONCURRENTLY_MODIFIED = "RESOURCE_CONCURRENTLY_MODIFIED";
    public static final String INVALID_PAGE = "INVALID_PAGE";
    public static final String INVALID_PAGE_SIZE = "INVALID_PAGE_SIZE";

    private ErrorCodes() {
    }
}
