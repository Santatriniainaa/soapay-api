package com.soapay.api.application.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record RefundResponse(
    String refundReference,
    String transactionReference,
    BigDecimal amount,
    String status,
    boolean simulated,
    String reason,
    Instant createdAt,
    Instant processedAt) {
}
