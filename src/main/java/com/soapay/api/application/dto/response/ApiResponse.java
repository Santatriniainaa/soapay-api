package com.soapay.api.application.dto.response;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.ApiStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ApiResponse(
        String transactionReference,
        ApiProvider provider,
        BigDecimal amount,
        String currency,
        ApiStatus status,
        boolean simulated,
        String message,
        Instant createdAt,
        Instant updatedAt
) {
}
