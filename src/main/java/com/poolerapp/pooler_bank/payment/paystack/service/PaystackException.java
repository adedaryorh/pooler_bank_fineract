package com.poolerapp.pooler_bank.payment.paystack.service;

public class PaystackException extends RuntimeException {

    private final int statusCode;

    public PaystackException(String message) {
        super(message);
        this.statusCode = 502;
    }

    public PaystackException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public PaystackException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 502;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
