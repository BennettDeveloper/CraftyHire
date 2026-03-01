package com.app.craftyhire.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers.
 *
 * Converts exceptions into consistent JSON error responses instead of
 * letting Spring's default error handling return stack traces or HTML pages.
 *
 * All error responses follow this shape:
 *   { "error": "Human-readable message" }
 *
 * Validation errors additionally include a "fields" map:
 *   { "error": "Validation failed", "fields": { "email": "Must be a valid email address" } }
 *
 * Scalability note: Add a request ID or trace ID to error responses here
 * when distributed tracing is added (e.g., Spring Cloud Sleuth / Micrometer Tracing).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles invalid input — e.g., passwords don't match, email already taken,
     * unsupported file type, invalid token.
     * Returns 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.debug("Bad request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * Handles failed login attempts (wrong email or password).
     * Returns a generic 401 message — never reveal which field was wrong
     * to prevent user enumeration attacks.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException e) {
        log.debug("Authentication failed: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid email or password"));
    }

    /**
     * Handles cases where a user account is not found.
     * Returns 404 Not Found.
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UsernameNotFoundException e) {
        log.debug("User not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
    }

    /**
     * Handles @Valid annotation validation failures on request bodies.
     * Returns 400 Bad Request with a map of field-level error messages.
     *
     * Example response:
     * {
     *   "error": "Validation failed",
     *   "fields": {
     *     "email": "Must be a valid email address",
     *     "password": "Password must be at least 8 characters"
     *   }
     * }
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        // Collect all field errors into a map for the frontend to display inline
        Map<String, String> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first,   // keep first error if multiple for same field
                        LinkedHashMap::new           // preserve insertion order
                ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("fields", fieldErrors);

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Handles file uploads that exceed the configured size limit (default: 10MB).
     * Returns 400 Bad Request with a helpful message.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleFileSizeExceeded(MaxUploadSizeExceededException e) {
        log.debug("File upload too large: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "File is too large. Maximum allowed size is 10MB."));
    }

    /**
     * Catch-all handler for unexpected runtime errors (e.g., Claude API failures,
     * PDF parsing errors, database issues).
     *
     * Returns 500 Internal Server Error with a generic message to avoid
     * leaking internal details to clients.
     * The full exception is logged for debugging.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "An unexpected error occurred. Please try again."));
    }

    /**
     * Catch-all handler for any other checked exceptions not handled above.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "An unexpected error occurred. Please try again."));
    }
}