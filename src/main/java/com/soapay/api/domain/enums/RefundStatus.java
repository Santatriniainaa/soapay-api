package com.soapay.api.domain.enums;

public enum RefundStatus {
    PENDING,
    REFUNDED,
    FAILED;

    public boolean isTerminal() {
        return this == REFUNDED || this == FAILED;
    }
}