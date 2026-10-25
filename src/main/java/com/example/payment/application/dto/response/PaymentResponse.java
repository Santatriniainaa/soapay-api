package com.example.payment.application.dto.response;

import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String transactionReference,
        PaymentProvider provider,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        boolean simulated,
        String message,
        Instant createdAt,
        Instant updatedAt
) {
}
