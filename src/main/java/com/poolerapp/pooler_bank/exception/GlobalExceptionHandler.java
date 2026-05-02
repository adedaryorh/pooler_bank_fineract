package com.poolerapp.pooler_bank.exception;

import com.poolerapp.pooler_bank.keycloak.KeycloakException;
import com.poolerapp.pooler_bank.payment.paystack.service.PaystackException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FineractException.class)
    public ResponseEntity<Map<String, Object>> handleFineract(FineractException ex) {
        return buildError(ex.getMessage(), HttpStatus.valueOf(ex.getStatusCode()));
    }

    @ExceptionHandler(LoanEligibilityException.class)
    public ResponseEntity<Map<String, Object>> handleEligibility(LoanEligibilityException ex) {
        return buildError(ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildError(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(PaystackException.class)
    public ResponseEntity<Map<String, Object>> handlePaystack(PaystackException ex) {
        return buildError("Payment gateway error: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(KeycloakException.class)
    public ResponseEntity<Map<String, Object>> handleKeycloak(KeycloakException ex) {
        return buildError("Identity provider error: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildError("Internal server error: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> buildError(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
