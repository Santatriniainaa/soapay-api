package com.example.payment.domain.exception;

public class PaymentException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public PaymentException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
