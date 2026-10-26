package com.soapay.api.application.dto.request;

import com.soapay.api.domain.enums.ApiProvider;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateApiRequest(
        @NotNull 
        ApiProvider provider,

        @NotBlank @Pattern(
                regexp = "03[234][0-9]{7}",
                message = "Le numéro doit être un numéro de démonstration au format 03xxxxxxxx")
        String phoneNumber,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) 
        BigDecimal amount,

        @NotBlank @Pattern(regexp = "[A-Z]{3}") 
        String currency,
        
        @Size(max = 255) 
        String description
) {
}
