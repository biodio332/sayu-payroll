package com.sayu.payroll.exception;

/**
 * Raised when the uploaded Excel can't be parsed/processed.
 */
public class PayrollProcessingException extends RuntimeException {
    public PayrollProcessingException(String message) {
        super(message);
    }

    public PayrollProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}

