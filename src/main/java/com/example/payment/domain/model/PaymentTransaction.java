package com.example.payment.domain.model;

import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.PaymentStatus;
import com.example.payment.domain.exception.PaymentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransaction(
        UUID id,
        String reference,
        String idempotencyKey,
        String clientId,
        PaymentProvider provider,
        String phoneNumber,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String description,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static PaymentTransaction create(
            String key,
            String clientId,
            PaymentProvider provider,
            String phone,
            BigDecimal amount,
            String currency,
            String description) {
        Instant now = Instant.now();
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new PaymentTransaction(
                UUID.randomUUID(),
                reference,
                key,
                clientId,
                provider,
                phone,
                amount,
                currency,
                PaymentStatus.CREATED,
                description,
                null,
                null,
                now,
                now,
                null);
    }

    public PaymentTransaction transitionTo(PaymentStatus next, String errorCode, String errorMessage) {
        boolean allowed = switch (status) {
            case CREATED -> next == PaymentStatus.PENDING || next == PaymentStatus.SUCCESS
                    || next == PaymentStatus.FAILED || next == PaymentStatus.CANCELLED;
            case PENDING -> next == PaymentStatus.SUCCESS || next == PaymentStatus.FAILED
                    || next == PaymentStatus.CANCELLED;
            case SUCCESS, PARTIALLY_REFUNDED -> next == PaymentStatus.REFUND_PENDING;
            case REFUND_PENDING -> next == PaymentStatus.REFUNDED || next == PaymentStatus.PARTIALLY_REFUNDED
                    || next == PaymentStatus.SUCCESS;
            default -> false;
        };
        if (!allowed) {
            throw new PaymentException("INVALID_STATE_TRANSITION", "Transition de statut interdite", 409);
        }
        Instant now = Instant.now();
        boolean completed = next == PaymentStatus.SUCCESS || next == PaymentStatus.FAILED
                || next == PaymentStatus.CANCELLED;
        return new PaymentTransaction(
                id,
                reference,
                idempotencyKey,
                clientId,
                provider,
                phoneNumber,
                amount,
                currency,
                next,
                description,
                errorCode,
                errorMessage,
                createdAt,
                now,
                completed ? now : completedAt);
    }
}
