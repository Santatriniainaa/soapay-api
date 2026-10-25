package com.soapay.api.domain.enums;

public enum ApiStatus {
    CREATED,
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED;

    public boolean isTerminal() {
        return switch (this) {
            case FAILED, CANCELLED, REFUNDED -> true;
            case CREATED, PENDING, SUCCESS, REFUND_PENDING, PARTIALLY_REFUNDED -> false;
        };
    }
}
