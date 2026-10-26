package com.soapay.api.application.usecase;

import com.soapay.api.application.dto.request.CreateApiRequest;
import com.soapay.api.application.dto.response.ApiResponse;
import com.soapay.api.application.port.out.ApiProviderPort;
import com.soapay.api.application.port.out.ApiTransactionStore;
import com.soapay.api.domain.exception.ApiException;
import com.soapay.api.domain.model.ApiTransaction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class ApiCreationService {
    private final ApiTransactionStore store;
    private final ApiProviderRegistry providers;
    private final ApiCreationAttemptService attemptService;

    public ApiCreationService(
            ApiTransactionStore store,
            ApiProviderRegistry providers,
            ApiCreationAttemptService attemptService) {
        this.store = store;
        this.providers = providers;
        this.attemptService = attemptService;
    }

    public ApiResponse create(CreateApiRequest request, String key, String clientId) {
        validateIdempotencyKey(key);
        if (request == null) {
            throw new ApiException(
                "INVALID_REQUEST", 
                "La requête de paiement est requise", 
                400);
        }
        if (request.provider() == null || request.phoneNumber() == null || request.phoneNumber().isBlank()) {
            throw new ApiException(
                "INVALID_REQUEST", 
                "Le fournisseur et le numéro sont requis", 
                400);
        }
        var existing = store.findByClientAndKey(clientId, key);
        if (existing.isPresent()) {
            return existingResponse(existing.get(), request);
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(
                "INVALID_AMOUNT", 
                "Le montant doit être strictement positif", 
                400);
        }
        if (request.amount().stripTrailingZeros().scale() > 2
                || request.amount().precision() - request.amount().scale() > 12) {
            throw new ApiException(
                "INVALID_AMOUNT_PRECISION", 
                "Le montant doit respecter au maximum 2 décimales", 
                400);
        }
        if (request.currency() == null || !request.currency().matches("[A-Z]{3}")) {
            throw new ApiException(
                "INVALID_CURRENCY", 
                "La devise doit être un code ISO 4217 reconnu", 
                400);
        }
        try {
            java.util.Currency.getInstance(request.currency());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                "INVALID_CURRENCY", 
                "La devise doit être un code ISO 4217 reconnu", 
                400);
        }

        ApiProviderPort provider = providers.get(request.provider());
        if (!provider.isAvailable()) {
            throw new ApiException(
                "PROVIDER_UNAVAILABLE", 
                "Fournisseur simulé indisponible", 
                503);
        }
        if (!provider.isPhoneNumberValid(request.phoneNumber())) {
            throw new ApiException(
                "INVALID_PHONE_NUMBER", 
                "Le numéro fictif n'est pas valide", 
                422);
        }

        try {
            return existingResponse(attemptService.execute(request, key, clientId, provider), request);
        } catch (DataIntegrityViolationException duplicateKey) {
            return store.findByClientAndKey(clientId, key)
                .map(prior -> existingResponse(prior, request))
                .orElseThrow(() -> duplicateKey);
        }
    }

    private static ApiResponse existingResponse(ApiTransaction prior, CreateApiRequest request) {
        boolean sameRequest = prior.provider() == request.provider()
                && prior.phoneNumber().equals(request.phoneNumber())
                && prior.amount().compareTo(request.amount()) == 0
                && prior.currency().equals(request.currency())
                && Objects.equals(prior.description(), request.description());
        if (!sameRequest) {
            throw new ApiException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Cette clé a déjà été utilisée avec un contenu différent",
                    409);
        }
        return toResponse(prior);
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ApiException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "L'en-tête Idempotency-Key est requis (128 caractères maximum)",
                    400);
        }
    }

    static ApiResponse toResponse(ApiTransaction tx) {
        String message = switch (tx.status()) {
            case SUCCESS -> "Paiement simulé accepté";
            case PENDING -> "Paiement simulé en attente de confirmation";
            case FAILED -> tx.failureMessage() == null ? "Paiement simulé refusé" : tx.failureMessage();
            case CANCELLED -> "Paiement simulé annulé";
            default -> "État de paiement simulé";
        };
        return new ApiResponse(
                tx.reference(), 
                tx.provider(), 
                tx.amount(), 
                tx.currency(), 
                tx.status(), 
                true,
                message, 
                tx.createdAt(), 
                tx.updatedAt());
    }
}
