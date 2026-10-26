package com.soapay.api.domain.model;

import com.soapay.api.domain.enums.RefundStatus;
import com.soapay.api.domain.exception.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApiRefund(
    UUID id,
    String reference,
    String idempotencyKey,
    String clientId,
    String transactionReference,
    BigDecimal amount,
    RefundStatus status,
    String reason,
    Instant createdAt,
    Instant processedAt) {

    public static ApiRefund pending(
        String key,
        String client,
        String apiReference,
        BigDecimal amount,
        String reason) {
        if (key == null || key.isBlank() || key.length() > 128
                || client == null || client.isBlank() || client.length() > 100
                || apiReference == null || apiReference.isBlank() || apiReference.length() > 32
                || (reason != null && reason.length() > 255)) {
            throw new ApiException(
                "INVALID_REFUND", 
                "Les informations du remboursement sont invalides", 
                400);
        }
        validateAmount(amount);
        Instant now = Instant.now();
        String reference = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new ApiRefund(
                UUID.randomUUID(), 
                reference, 
                key, 
                client, 
                apiReference,
                amount, 
                RefundStatus.PENDING, 
                reason, 
                now, 
                null);
    }

    public ApiRefund transitionTo(RefundStatus next) {
        boolean allowed = status == RefundStatus.PENDING
                && (next == RefundStatus.REFUNDED || next == RefundStatus.FAILED);
        if (!allowed) {
            throw new ApiException(
                "INVALID_REFUND_TRANSITION", 
                "Transition de remboursement interdite", 
                409);
        }
        return new ApiRefund(
                id, 
                reference, 
                idempotencyKey, 
                clientId, 
                transactionReference,
                amount, 
                next, 
                reason, 
                createdAt, 
                Instant.now());
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(
                "INVALID_AMOUNT", 
                "Le montant doit être strictement positif", 
                400);
        }
        if (amount.stripTrailingZeros().scale() > 2 || amount.precision() - amount.scale() > 12) {
            throw new ApiException(
                "INVALID_AMOUNT_PRECISION", 
                "Le montant doit respecter au maximum 2 décimales", 
                400);
        }
    }
}
