package com.neobank.module.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.neobank.module.service.PolicyConfigValidationException;
import com.neobank.module.service.CaseNotFoundException;
import com.neobank.module.service.ReferralConflictException;

/**
 * Turns exceptions into a stable JSON error shape, so the front end and the orchestrator get a
 * predictable body instead of a stack trace — {@code server.error.include-*=never} in
 * {@code application.yml} makes sure nothing leaks past this class.
 *
 * <p>Add a handler per exception your own code throws. A lookup that finds nothing, for example:</p>
 *
 * <pre>{@code
 * @ExceptionHandler(NoSuchElementException.class)
 * public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
 *     return error(HttpStatus.NOT_FOUND, ex.getMessage());
 * }
 * }</pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * A field failed validation — in practice, an envelope with no {@code applicationId}. The
     * message names the field, because "400 Bad Request" alone tells the sender nothing.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return validationError(errors);
    }

    /**
     * The body was not readable at all — broken JSON, or a value of the wrong type.
     *
     * <p>Worth handling explicitly because you will meet it: the sidecar lets you edit the envelope
     * before sending, and a stray comma otherwise comes back as an empty {@code 400} with nothing
     * to read. Only the first line of Jackson's message is returned; the rest is a parser trace
     * that means nothing to the caller.</p>
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        int newline = message == null ? -1 : message.indexOf('\n');
        return error(HttpStatus.BAD_REQUEST,
                "malformed request body: " + (newline > 0 ? message.substring(0, newline) : message));
    }

    /**
     * A {@code POST /config} document failed a cross-field business rule (UC07) — a country on
     * both residency lists, a restriction entry missing a field, or {@code sampleEvery < 1}.
     */
    @ExceptionHandler(PolicyConfigValidationException.class)
    public ResponseEntity<Map<String, Object>> handlePolicyConfigValidation(
            PolicyConfigValidationException ex) {
        List<Map<String, String>> errors = ex.getErrors().stream()
                .map(violation -> fieldError(violation.field(), violation.message()))
                .toList();
        return validationError(errors);
    }

    private ResponseEntity<Map<String, Object>> validationError(List<Map<String, String>> errors) {
        String message = errors.stream()
                .map(error -> error.get("field") + " " + error.get("message"))
                .reduce((first, second) -> first + "; " + second)
                .orElse("validation failed");
        Map<String, Object> body = errorBody(HttpStatus.BAD_REQUEST, message);
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private Map<String, String> fieldError(String field, String message) {
        Map<String, String> error = new LinkedHashMap<>();
        error.put("field", field);
        error.put("message", message);
        return error;
    }

    @ExceptionHandler(CaseNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCaseNotFound(CaseNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ReferralConflictException.class)
    public ResponseEntity<Map<String, Object>> handleReferralConflict(
            ReferralConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(errorBody(status, message));
    }


    private Map<String, Object> errorBody(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return body;
    }
}
