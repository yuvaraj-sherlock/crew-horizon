package com.crewhorizon.authservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 * Global Exception Handler
 * ============================================================
 * WHAT: Centralized exception handling for ALL exceptions thrown
 *       anywhere in the auth-service application layer.
 *       Converts exceptions into structured, client-friendly
 *       HTTP responses.
 *
 * WHY @RestControllerAdvice (not @ExceptionHandler per controller):
 *       1. SINGLE SOURCE OF TRUTH: All error response formatting
 *          is in one place — consistent format across all endpoints.
 *       2. DRY PRINCIPLE: No try-catch blocks in controllers.
 *          Controllers stay clean and focused on happy path.
 *       3. CONSISTENT ERROR FORMAT: API consumers can rely on a
 *          predictable error response structure for all failures.
 *       4. SECURITY: Prevents stack traces from leaking to clients
 *          (which would reveal internal implementation details).
 *
 * WHY NOT return exception messages directly:
 *       Exception messages may contain sensitive info (table names,
 *       query strings, internal class names). We log the full
 *       exception internally but return safe, generic messages
 *       to the client.
 *
 * ERROR RESPONSE FORMAT:
 * {
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "...",
 *   "timestamp": "2024-01-01T00:00:00Z",
 *   "path": "/api/v1/auth/login",
 *   "errors": {}  // only for validation errors
 * }
 * ============================================================
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles Bean Validation failures (@Valid on DTOs).
     *
     * WHY return field-level errors (not just a generic message):
     * Frontend applications need to know WHICH fields failed
     * validation to display inline error messages next to the
     * relevant form fields. A generic "validation failed" message
     * forces the user to guess what's wrong.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message("Request contains invalid fields")
                .path(extractPath(request))
                .timestamp(Instant.now())
                .fieldErrors(fieldErrors)
                .build();

        log.warn("Validation error on {}: {}", extractPath(request), fieldErrors);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request) {
        // WHY generic message for credentials errors:
        // "Invalid email or password" (not "email not found") prevents
        // user enumeration — attacker can't determine if email exists.
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication Failed",
                "Invalid email or password", request);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex, WebRequest request) {
        log.warn("Account locked access attempt: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.LOCKED, "Account Locked",
                ex.getMessage(), request);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found",
                ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(
            DuplicateResourceException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Conflict",
                ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid Token",
                ex.getMessage(), request);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            ValidationException ex, WebRequest request) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request",
                ex.getMessage(), request);
    }

    /**
     * WHY catch-all handler:
     * Unexpected exceptions (NullPointerException, DB connection errors)
     * must NEVER expose stack traces to clients. This handler ensures
     * all unhandled exceptions return a safe 500 response while the
     * full exception is logged internally for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {
        // Log full stack trace internally (not returned to client)
        log.error("Unhandled exception at {}: {}", extractPath(request), ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please contact support if this persists.",
                request);
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String error, String message, WebRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .path(extractPath(request))
                .timestamp(Instant.now())
                .build();
        return ResponseEntity.status(status).body(errorResponse);
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }
}
