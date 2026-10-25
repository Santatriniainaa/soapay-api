package com.soapay.api.domain.model;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.exception.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Currency;

public record ApiTransaction(
        UUID id,
        String reference,
        String idempotencyKey,
        String clientId,
        ApiProvider provider,
        String phoneNumber,
        BigDecimal amount,
        String currency,
        ApiStatus status,
        String description,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt
) {
    public static ApiTransaction create(
            String key,
            String clientId,
            ApiProvider provider,
            String phone,
            BigDecimal amount,
            String currency,
            String description) {
                validateDetails(key, clientId, provider, phone, amount, currency);
        Instant now = Instant.now();
        String reference = "PAY-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        return new ApiTransaction(
                UUID.randomUUID(),
                reference,
                key,
                clientId,
                provider,
                phone,
                amount,
                currency,
                ApiStatus.CREATED,
                description,
                null,
                null,
                now,
                now,
                null);
    }

        private static void validateDetails(
                        String key,
                        String clientId,
                        ApiProvider provider,
                        String phone,
                        BigDecimal amount,
                        String currency) {
                if (key == null || key.isBlank() || key.length() > 128
                                || clientId == null || clientId.isBlank() || clientId.length() > 100
                                || provider == null || phone == null || !phone.matches("03[234][0-9]{7}")) {
                        throw new ApiException("INVALID_TRANSACTION", "Les informations du paiement sont invalides", 400);
                }
                validateAmount(amount);
                if (currency == null || !currency.matches("[A-Z]{3}")) {
                        throw new ApiException("INVALID_CURRENCY", "La devise doit être un code ISO 4217 reconnu", 400);
                }
                try {
                        Currency.getInstance(currency);
                } catch (IllegalArgumentException exception) {
                        throw new ApiException("INVALID_CURRENCY", "La devise doit être un code ISO 4217 reconnu", 400);
                }
        }

        private static void validateAmount(BigDecimal amount) {
                if (amount == null || amount.signum() <= 0) {
                        throw new ApiException("INVALID_AMOUNT", "Le montant doit être strictement positif", 400);
                }
                if (amount.stripTrailingZeros().scale() > 2 || amount.precision() - amount.scale() > 12) {
                        throw new ApiException("INVALID_AMOUNT_PRECISION", "Le montant doit respecter au maximum 2 décimales", 400);
                }
        }

    public ApiTransaction transitionTo(ApiStatus next, String errorCode, String errorMessage) {
        boolean allowed = switch (status) {
            case CREATED -> next == ApiStatus.PENDING || next == ApiStatus.SUCCESS
                    || next == ApiStatus.FAILED || next == ApiStatus.CANCELLED;
            case PENDING -> next == ApiStatus.SUCCESS || next == ApiStatus.FAILED
                    || next == ApiStatus.CANCELLED;
            case SUCCESS, PARTIALLY_REFUNDED -> next == ApiStatus.REFUND_PENDING;
            case REFUND_PENDING -> next == ApiStatus.REFUNDED || next == ApiStatus.PARTIALLY_REFUNDED
                    || next == ApiStatus.SUCCESS;
            default -> false;
        };
        if (!allowed) {
            throw new ApiException("INVALID_STATE_TRANSITION", "Transition de statut interdite", 409);
        }
        Instant now = Instant.now();
        boolean completed = next == ApiStatus.SUCCESS || next == ApiStatus.FAILED
                || next == ApiStatus.CANCELLED;
        return new ApiTransaction(
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
