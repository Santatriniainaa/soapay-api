package com.example.payment.application.usecase;

import com.example.payment.application.dto.request.CreatePaymentRequest;
import com.example.payment.application.dto.response.PaymentResponse;
import com.example.payment.application.port.out.PaymentProviderPort;
import com.example.payment.application.port.out.PaymentTransactionStore;
import com.example.payment.domain.enums.PaymentStatus;
import com.example.payment.domain.enums.SimulationResult;
import com.example.payment.domain.exception.PaymentException;
import com.example.payment.domain.model.PaymentTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class PaymentService {
    private final PaymentTransactionStore store;
    private final PaymentProviderRegistry providers;

    public PaymentService(PaymentTransactionStore store, PaymentProviderRegistry providers) {
        this.store = store;
        this.providers = providers;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request, String key, String clientId) {
        validateIdempotencyKey(key);

        var existing = store.findByClientAndKey(clientId, key);
        if (existing.isPresent()) {
            PaymentTransaction prior = existing.get();
            boolean sameRequest = prior.provider() == request.provider()
                    && prior.phoneNumber().equals(request.phoneNumber())
                    && prior.amount().compareTo(request.amount()) == 0
                    && prior.currency().equals(request.currency())
                    && Objects.equals(prior.description(), request.description());
            if (!sameRequest) {
                throw new PaymentException(
                        "IDEMPOTENCY_KEY_REUSED",
                        "Cette clé a déjà été utilisée avec un contenu différent",
                        409);
            }
            return toResponse(prior);
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentException("INVALID_AMOUNT", "Le montant doit être strictement positif", 400);
        }

        try {
            java.util.Currency.getInstance(request.currency());
        } catch (IllegalArgumentException exception) {
            throw new PaymentException("INVALID_CURRENCY", "La devise doit être un code ISO 4217 reconnu", 400);
        }

        PaymentProviderPort provider = providers.get(request.provider());
        if (!provider.isAvailable()) {
            throw new PaymentException("PROVIDER_UNAVAILABLE", "Fournisseur simulé indisponible", 503);
        }
        if (!provider.isPhoneNumberValid(request.phoneNumber())) {
            throw new PaymentException("INVALID_PHONE_NUMBER", "Le numéro fictif n'est pas valide", 422);
        }

        PaymentTransaction tx = PaymentTransaction.create(
                key,
                clientId,
                request.provider(),
                request.phoneNumber(),
                request.amount(),
                request.currency(),
                request.description());
        SimulationResult result = provider.simulatePayment(request.phoneNumber(), request.amount());
        tx = switch (result) {
            case SUCCESS -> tx.transitionTo(PaymentStatus.SUCCESS, null, null);
            case PENDING -> tx.transitionTo(PaymentStatus.PENDING, null, null);
            case FAILED -> tx.transitionTo(
                    PaymentStatus.FAILED, "SIMULATED_DECLINE", "Refus simulé par le fournisseur");
            case TIMEOUT -> tx.transitionTo(
                    PaymentStatus.FAILED, "SIMULATED_TIMEOUT", "Délai simulé dépassé");
        };
        return toResponse(store.save(tx));
    }

    private static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new PaymentException(
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "L'en-tête Idempotency-Key est requis (128 caractères maximum)",
                    400);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(String reference, String clientId) {
        return store.findByReferenceAndClient(reference, clientId).map(PaymentService::toResponse)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> list(String clientId) {
        return store.findRecentByClient(clientId).stream().map(PaymentService::toResponse).toList();
    }

    @Transactional
    public PaymentResponse cancel(String reference, String clientId) {
        PaymentTransaction tx = store.findByReferenceAndClientForUpdate(reference, clientId)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404));
        return toResponse(store.save(tx.transitionTo(PaymentStatus.CANCELLED, null, null)));
    }

    @Transactional
    public PaymentResponse confirm(String reference) {
        PaymentTransaction tx = store.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404));
        if (tx.status() != PaymentStatus.PENDING) {
            throw new PaymentException("INVALID_STATE_TRANSITION", "Seule une transaction en attente peut être confirmée", 409);
        }
        return toResponse(store.save(tx.transitionTo(PaymentStatus.SUCCESS, null, null)));
    }

    private static PaymentResponse toResponse(PaymentTransaction tx) {
        String message = switch (tx.status()) {
            case SUCCESS -> "Paiement simulé accepté";
            case PENDING -> "Paiement simulé en attente de confirmation";
            case FAILED -> tx.failureMessage() == null ? "Paiement simulé refusé" : tx.failureMessage();
            case CANCELLED -> "Paiement simulé annulé";
            default -> "État de paiement simulé";
        };
        return new PaymentResponse(
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
