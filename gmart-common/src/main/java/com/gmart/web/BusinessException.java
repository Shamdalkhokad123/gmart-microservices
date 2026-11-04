package com.gmart.web;

/**
 * An expected problem whose message is written for the customer, e.g. "Your cart is empty".
 * Its message is safe to show; any other exception is logged and replaced by a generic message.
 */
public class BusinessException extends RuntimeException {

    private final int status;

    public BusinessException(String message) {
        this(message, 400);
    }

    public BusinessException(String message, int status) {
        super(message);
        this.status = status;
    }

    public BusinessException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
