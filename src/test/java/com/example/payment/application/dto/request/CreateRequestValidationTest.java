package com.example.payment.application.dto.request;

import com.example.payment.domain.enums.PaymentProvider;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateRequestValidationTest {
    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validPaymentRequestPassesValidation() {
        CreatePaymentRequest request = new CreatePaymentRequest(PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("0.01"), "MGA", "Test");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void invalidPaymentFieldsAreReported() {
        CreatePaymentRequest request = new CreatePaymentRequest(null,
                "034000000", new BigDecimal("0.001"), "mga", "x".repeat(256));

        Set<String> invalidFields = validator.validate(request).stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());

        assertEquals(Set.of("provider", "phoneNumber", "amount", "currency", "description"), invalidFields);
    }

    @Test
    void validRefundRequestPassesValidation() {
        CreateRefundRequest request = new CreateRefundRequest(new BigDecimal("999999999999.99"), "Remboursement");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void refundAmountAndReasonLimitsAreEnforced() {
        CreateRefundRequest request = new CreateRefundRequest(new BigDecimal("1000000000000.00"), "x".repeat(256));

        assertEquals(2, validator.validate(request).size());
    }
}
