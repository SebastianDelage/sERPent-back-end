package com.empresa.serpent.shared.api;

import com.empresa.serpent.shared.exception.BusinessException;
import com.empresa.serpent.shared.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Business errors: the message is already user-facing Spanish.
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest req) {
        return build(ex.status(), ex.getMessage(), req, Map.of());
    }

    // Not found: generic Spanish message; technical detail stays in the logs.
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        log.warn("Not found at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "No se encontró el recurso solicitado.", req, Map.of());
    }

    /**
     * Bean validation rejected the body: one entry per field under {@code details}.
     *
     * <p>ORDER MATTERS AND A HashMap HAS NONE. These messages are shown to the operator, and
     * a hash order means the same rejection can list its reasons differently on two
     * consecutive tries — the sort of difference nobody can explain and everybody notices.
     * TreeMap orders by field name: arbitrary as a criterion, but stable, which is the
     * property that matters here.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, Object> details = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> details.put(err.getField(), err.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(err -> details.put(err.getObjectName(), err.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Revisá los datos ingresados.", req, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    /** Same as above: TreeMap so the reasons come back in a stable order. */
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, Object> details = new TreeMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            details.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return build(HttpStatus.BAD_REQUEST, "Revisá los datos ingresados.", req, details);
    }

    /*
     * THESE TWO PUT THE PARAMETER NAME IN THE LOG, NOT IN details.
     *
     * They used to answer with details = {"parameter": "warehouseId"}, and that broke the
     * contract the front end relies on: every VALUE in details is an operator-facing sentence,
     * so the interceptor can list them without inspecting the text. A bare field name in there
     * put "warehouseId" on the cashier´s screen — caught by the interceptor´s own test.
     *
     * Nothing is lost: a malformed query parameter is not something the operator can fix, and
     * the name is what someone debugging needs, which is what a log is for.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        log.warn("Type mismatch at {} on parameter {}", req.getRequestURI(), ex.getName());
        return build(HttpStatus.BAD_REQUEST, "El valor de un parámetro no es válido.", req, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        log.warn("Missing parameter at {}: {}", req.getRequestURI(), ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Falta un dato requerido en la solicitud.", req, Map.of());
    }

    // Safety net for any IllegalArgumentException not yet migrated to a BusinessException.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        log.warn("Illegal argument at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "La solicitud no es válida.", req, Map.of());
    }

    /**
     * A role restriction refused the request.
     *
     * <p>Thrown by Spring Security's @PreAuthorize, which is not a BusinessException and so
     * would otherwise fall through to the catch-all and surface as a 500 — telling the user
     * something broke when in fact the system worked exactly as intended.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied at {}: {}", req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN,
                "No tenés permiso para realizar esta acción.", req, Map.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : "No se pudo procesar la solicitud.";
        return build(status, message, req, Map.of());
    }

    // No controller/static resource matches the request path (e.g. a removed or misspelled endpoint).
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No se encontró el recurso solicitado.", req, Map.of());
    }

    // A DB constraint rejected the write. Never leak the raw SQL/constraint name to the client.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), ex.getMessage());

        if ("23505".equals(sqlState(ex))) {
            return build(HttpStatus.CONFLICT, "Ya existe un registro con esos datos.", req, Map.of());
        }
        return build(HttpStatus.BAD_REQUEST, "Los datos ingresados no se pudieron guardar.", req, Map.of());
    }

    /** Walks the cause chain for the underlying SQLState (Postgres: 23505 = unique_violation). */
    private String sqlState(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }

    // Anything unexpected: never leak internals to the client.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error at {}", req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Volvé a intentarlo en unos minutos.", req, Map.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req, Map<String, Object> details) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                req.getRequestURI(),
                details == null ? Map.of() : details
        );
        return ResponseEntity.status(status).body(body);
    }
}