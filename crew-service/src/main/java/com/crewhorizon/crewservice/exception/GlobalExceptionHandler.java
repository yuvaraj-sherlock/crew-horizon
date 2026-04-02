package com.crewhorizon.crewservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for Crew Service.
 * WHY: Centralizes all error responses — consistent format across all endpoints.
 * See auth-service GlobalExceptionHandler for detailed comments.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CrewMemberNotFoundException.class)
    public ResponseEntity<Map<String,Object>> handleNotFound(CrewMemberNotFoundException ex, WebRequest req) {
        return buildError(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }
    @ExceptionHandler(DuplicateCrewMemberException.class)
    public ResponseEntity<Map<String,Object>> handleDuplicate(DuplicateCrewMemberException ex, WebRequest req) {
        return buildError(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> handleValidation(MethodArgumentNotValidException ex, WebRequest req) {
        Map<String,String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(e -> fieldErrors.put(((FieldError)e).getField(), e.getDefaultMessage()));
        Map<String,Object> body = buildErrorBody(400, "Validation Failed", "Invalid request fields", req);
        body.put("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleGeneric(Exception ex, WebRequest req) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", req);
    }
    private ResponseEntity<Map<String,Object>> buildError(HttpStatus status, String error, String message, WebRequest req) {
        return ResponseEntity.status(status).body(buildErrorBody(status.value(), error, message, req));
    }
    private Map<String,Object> buildErrorBody(int status, String error, String message, WebRequest req) {
        Map<String,Object> body = new HashMap<>();
        body.put("status", status); body.put("error", error);
        body.put("message", message);
        body.put("path", req.getDescription(false).replace("uri=",""));
        body.put("timestamp", Instant.now().toString());
        return body;
    }
}
