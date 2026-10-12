package com.recoverai.backend.controller;

import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        String message = ex.getReason() == null ? "Request could not be completed" : ex.getReason();
        return ResponseEntity.status(code).body(Map.of("status", code.value(), "error", code.toString(), "message", message));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream().findFirst().map(e -> e.getField() + " is invalid").orElse("Request validation failed");
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request", "message", message));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable() {
        return ResponseEntity.badRequest().body(Map.of("status", 400, "error", "Bad Request", "message", "Request body is invalid"));
    }
}
