package com.poolerapp.pooler_bank.exception;

public class FineractException extends RuntimeException {
    private final int statusCode;

    public FineractException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public FineractException(String message) {
        super(message);
        this.statusCode = 500;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
