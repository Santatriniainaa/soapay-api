package com.soapay.api.application.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateRefundRequest(
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) 
    BigDecimal amount,

    @Size(max = 255) 
    String reason
) {
}
