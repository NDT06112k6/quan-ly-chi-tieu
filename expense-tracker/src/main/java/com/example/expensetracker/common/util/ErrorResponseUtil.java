package com.example.expensetracker.common.util;

import com.example.expensetracker.common.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

public final class ErrorResponseUtil {

    private ErrorResponseUtil() {
    }

    public static ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message) {
        ErrorResponse errorResponse = new ErrorResponse(code, message, Instant.now());
        ResponseEntity<ErrorResponse> response = ResponseEntity.status(status).body(errorResponse);
        return response;
    }
}
