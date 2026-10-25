package com.example.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefund(
    UUID id,
    String reference,
    String idempotencyKey,
    String clientId,
    String transactionReference,
    BigDecimal amount,
    String status,
    String reason,
    Instant createdAt,
    Instant processedAt) {

    public static PaymentRefund completed(
        String key,
        String client,
        String paymentReference,
        BigDecimal amount,
        String reason) {
        Instant now = Instant.now();
    String reference = "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    return new PaymentRefund(
        UUID.randomUUID(), reference, key, client, paymentReference,
        amount, "REFUNDED", reason, now, now);
    }
}
