package com.example.payment.domain.enums;

public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == CANCELLED || this == REFUNDED;
    }
}
