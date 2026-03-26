package com.sayu.payroll.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PayrollProcessingException.class)
    public ResponseEntity<Map<String, String>> handlePayrollProcessingException(PayrollProcessingException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "error", "PAYROLL_PROCESSING_ERROR",
                        "message", ex.getMessage()
                ));
    }
}

