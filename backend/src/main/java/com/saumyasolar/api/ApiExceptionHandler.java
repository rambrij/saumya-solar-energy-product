package com.saumyasolar.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = exception.getReason() == null || exception.getReason().isBlank()
                ? status.getReasonPhrase() : exception.getReason();
        return ResponseEntity.status(status).body(body(status, detail));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST,
                detail.isBlank() ? "Request validation failed." : detail));
    }

    private Map<String, Object> body(HttpStatus status, String detail) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "about:blank");
        result.put("title", status.getReasonPhrase());
        result.put("status", status.value());
        result.put("detail", detail);
        result.put("timestamp", Instant.now().toString());
        return result;
    }
}
