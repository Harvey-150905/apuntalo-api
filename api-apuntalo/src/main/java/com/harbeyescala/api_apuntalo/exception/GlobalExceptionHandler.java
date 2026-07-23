package com.harbeyescala.api_apuntalo.exception;

import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Comparator;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> resourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(StoreNotFoundException.class)
    public ResponseEntity<ApiError> storeNotFound(StoreNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "STORE_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(CashRegisterNotFoundException.class)
    public ResponseEntity<ApiError> cashRegisterNotFound(CashRegisterNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CASH_REGISTER_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(CashSessionNotFoundException.class)
    public ResponseEntity<ApiError> cashSessionNotFound(CashSessionNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CASH_SESSION_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiError> duplicate(DuplicateResourceException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", ex.getMessage(), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> unauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return error(HttpStatus.UNAUTHORIZED, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidFileException.class)
    public ResponseEntity<ApiError> invalidFile(InvalidFileException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_FILE, "El archivo proporcionado no es válido", request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> businessRule(BusinessRuleException ex, HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiError> concurrency(RuntimeException ex, HttpServletRequest request) {
        log.warn("Conflicto de concurrencia: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.CONFLICT, ErrorCodes.RESOURCE_CONCURRENTLY_MODIFIED,
                "El recurso fue modificado por otra petición. Vuelve a intentarlo.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violación de integridad de datos", ex);
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "La operación entra en conflicto con el estado actual del recurso.", request);
    }

    @ExceptionHandler({AccessDeniedException.class, ForbiddenOperationException.class})
    public ResponseEntity<ApiError> forbidden(RuntimeException ex, HttpServletRequest request) {
        String code = ex instanceof ForbiddenOperationException functional
                ? functional.getCode() : ErrorCodes.ACCESS_DENIED;
        String message = ex instanceof ForbiddenOperationException
                ? ex.getMessage() : "No tienes permisos para realizar esta acción";
        return error(HttpStatus.FORBIDDEN, code, message, request);
    }

    @ExceptionHandler(PendingTicketsAcknowledgementException.class)
    public ResponseEntity<ApiError> pendingTickets(PendingTicketsAcknowledgementException ex,
                                                   HttpServletRequest request) {
        ApiError body = new ApiError(422, "PENDING_TICKETS_ACKNOWLEDGEMENT_REQUIRED",
                ex.getMessage(), request.getRequestURI(), null,
                Map.of("pendingTicketCount", ex.getPendingTicketCount(),
                        "pendingTicketAmount", ex.getPendingTicketAmount()));
        return ResponseEntity.unprocessableEntity().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> beanValidation(MethodArgumentNotValidException ex,
                                                   HttpServletRequest request) {
        List<ApiFieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiFieldError(field.getField(),
                        field.getCode() == null ? "Invalid" : field.getCode(), field.getDefaultMessage()))
                .sorted(fieldErrorComparator()).toList();
        return validationError(fields, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> constraintValidation(ConstraintViolationException ex,
                                                         HttpServletRequest request) {
        List<ApiFieldError> fields = ex.getConstraintViolations().stream().map(violation -> {
            String field = violation.getPropertyPath().toString();
            if (field.contains(".")) field = field.substring(field.lastIndexOf('.') + 1);
            String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            return new ApiFieldError(field, code, violation.getMessage());
        }).sorted(fieldErrorComparator()).toList();
        return validationError(fields, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String code = classifyUnreadableRequest(ex);
        String message = switch (code) {
            case ErrorCodes.INVALID_ENUM_VALUE -> "La petición contiene un valor de enum no válido";
            case ErrorCodes.INVALID_FIELD_TYPE -> "La petición contiene un campo con un tipo no válido";
            default -> "El cuerpo de la petición no es un JSON válido";
        };
        return error(HttpStatus.BAD_REQUEST, code, message, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> queryParameterTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request
    ) {
        String code = ex.getRequiredType() != null && ex.getRequiredType().isEnum()
                ? ErrorCodes.INVALID_ENUM_VALUE
                : ErrorCodes.INVALID_FIELD_TYPE;
        String message = code.equals(ErrorCodes.INVALID_ENUM_VALUE)
                ? "La petición contiene un valor de enum no válido"
                : "La petición contiene un parámetro con un tipo no válido";
        return error(HttpStatus.BAD_REQUEST, code, message, request);
    }

    private String classifyUnreadableRequest(HttpMessageNotReadableException exception) {
        boolean invalidEnum = false;
        boolean invalidFormat = false;
        boolean mismatchedInput = false;
        boolean inputCoercion = false;
        boolean fieldConversion = false;
        boolean parseError = false;

        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = exception;
        while (current != null && visited.add(current)) {
            String simpleName = current.getClass().getSimpleName();
            if (current instanceof InvalidFormatException format) {
                invalidFormat = true;
                Class<?> targetType = format.getTargetType();
                invalidEnum |= targetType != null && targetType.isEnum();
            } else if ("InvalidFormatException".equals(simpleName)) {
                invalidFormat = true;
                invalidEnum |= targetsEnumCompatibly(current);
            } else if (current instanceof MismatchedInputException
                    || "MismatchedInputException".equals(simpleName)) {
                mismatchedInput = true;
            }
            if (current instanceof InputCoercionException
                    || "InputCoercionException".equals(simpleName)) {
                inputCoercion = true;
            }
            if (current instanceof NumberFormatException
                    || current instanceof DateTimeParseException
                    || "NumberFormatException".equals(simpleName)) {
                fieldConversion = true;
            }
            if (current instanceof StreamReadException
                    || "JsonParseException".equals(simpleName)
                    || "StreamReadException".equals(simpleName)
                    || "JsonEOFException".equals(simpleName)) {
                parseError = true;
            }
            current = current.getCause();
        }

        if (invalidEnum) return ErrorCodes.INVALID_ENUM_VALUE;
        if (invalidFormat || mismatchedInput || inputCoercion || fieldConversion) {
            return ErrorCodes.INVALID_FIELD_TYPE;
        }
        if (parseError) return ErrorCodes.INVALID_REQUEST_BODY;
        return ErrorCodes.INVALID_REQUEST_BODY;
    }

    private boolean targetsEnumCompatibly(Throwable exception) {
        try {
            Object targetType = exception.getClass().getMethod("getTargetType").invoke(exception);
            return targetType instanceof Class<?> type && type.isEnum();
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return false;
        }
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> internalState(RuntimeException ex, HttpServletRequest request) {
        log.error("Inconsistencia interna en {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "Ha ocurrido un error interno", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Error interno no controlado en {}", request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "Ha ocurrido un error interno", request);
    }

    private ResponseEntity<ApiError> validationError(List<ApiFieldError> fields, HttpServletRequest request) {
        ApiError body = new ApiError(400, ErrorCodes.VALIDATION_FAILED, "Error de validación",
                request.getRequestURI(), fields, null);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), code, message, request.getRequestURI()));
    }

    private Comparator<ApiFieldError> fieldErrorComparator() {
        return Comparator.comparing(ApiFieldError::field, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ApiFieldError::code, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ApiFieldError::message, Comparator.nullsFirst(String::compareTo));
    }
}
