package com.soapay.api.application.usecase;

import com.soapay.api.application.dto.request.CreateRefundRequest;
import com.soapay.api.application.dto.response.RefundResponse;
import com.soapay.api.application.port.out.ApiRefundStore;
import com.soapay.api.application.port.out.ApiTransactionStore;

import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.enums.RefundStatus;
import com.soapay.api.domain.exception.ApiException;
import com.soapay.api.domain.model.ApiRefund;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class RefundService {
    private final ApiTransactionStore transactions;
    private final ApiRefundStore refunds;

    public RefundService(ApiTransactionStore transactions, ApiRefundStore refunds) {
        this.transactions = transactions; this.refunds = refunds;
    }

    @Transactional
    public RefundResponse create(String apiReference, CreateRefundRequest request, String key, String clientId) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new ApiException("IDEMPOTENCY_KEY_REQUIRED", "L'en-tête Idempotency-Key est requis (128 caractères maximum)", 400);
        }
        if (request == null || request.amount() == null || request.amount().signum() <= 0) {
            throw new ApiException("INVALID_AMOUNT", "Le montant doit être strictement positif", 400);
        }
        if (request.amount().stripTrailingZeros().scale() > 2
                || request.amount().precision() - request.amount().scale() > 12) {
            throw new ApiException("INVALID_AMOUNT_PRECISION", "Le montant doit respecter au maximum 2 décimales", 400);
        }

        var existing = findMatchingRequest(apiReference, request, key, clientId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        var api = transactions.findByReferenceAndClientForUpdate(apiReference, clientId)
                .orElseThrow(() -> new ApiException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404));

        // Recheck under the api lock: another request with this key may have
        // completed while this request waited for the lock.
        existing = findMatchingRequest(apiReference, request, key, clientId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // The api row lock serializes distinct refund keys for this transaction.
        if (api.status() != ApiStatus.SUCCESS && api.status() != ApiStatus.PARTIALLY_REFUNDED) {
            throw new ApiException("REFUND_NOT_ALLOWED", "Seule une transaction réussie peut être remboursée", 409);
        }

        BigDecimal alreadyRefunded = refunds.sumRefunded(apiReference);
        BigDecimal remaining = api.amount().subtract(alreadyRefunded);
        if (request.amount().compareTo(remaining) > 0) {
            throw new ApiException("REFUND_EXCEEDS_REMAINING", "Le remboursement dépasse le solde remboursable", 422);
        }

        ApiRefund pendingRefund = ApiRefund.pending(key, clientId, apiReference, request.amount(), request.reason());
        refunds.save(pendingRefund);
        ApiRefund refund = pendingRefund.transitionTo(RefundStatus.REFUNDED);
        refunds.save(refund);

        BigDecimal after = alreadyRefunded.add(request.amount());
        ApiStatus next = after.compareTo(api.amount()) == 0
                ? ApiStatus.REFUNDED
                : ApiStatus.PARTIALLY_REFUNDED;
        transactions.save(api.transitionTo(ApiStatus.REFUND_PENDING, null, null)
                .transitionTo(next, null, null));
        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(String apiReference, String clientId) {
        return list(apiReference, clientId, 0, 100);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(String apiReference, String clientId, int page, int size) {
        if (transactions.findByReferenceAndClient(apiReference, clientId).isEmpty())
            throw new ApiException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404);
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        return refunds.findByApiAndClient(apiReference, clientId, PageRequest.of(safePage, Math.min(safeSize, 100)))
                .stream().map(RefundService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse get(String reference, String clientId) {
        return refunds.findByReferenceAndClient(reference, clientId).map(RefundService::toResponse)
                .orElseThrow(() -> new ApiException("REFUND_NOT_FOUND", "Remboursement introuvable", 404));
    }

    private static RefundResponse toResponse(ApiRefund refund) {
        return new RefundResponse(refund.reference(), refund.transactionReference(), refund.amount(), refund.status().name(),
                true, refund.reason(), refund.createdAt(), refund.processedAt());
    }

    private java.util.Optional<ApiRefund> findMatchingRequest(
            String apiReference, CreateRefundRequest request, String key, String clientId) {
        var existing = refunds.findByClientAndKey(clientId, key);
        if (existing.isEmpty()) {
            return existing;
        }

        ApiRefund prior = existing.get();
        boolean sameRequest = prior.transactionReference().equals(apiReference)
                && prior.amount().compareTo(request.amount()) == 0
                && Objects.equals(prior.reason(), request.reason());
        if (!sameRequest) {
            throw new ApiException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Cette clé a déjà été utilisée avec un contenu différent",
                    409);
        }
        return existing;
    }
}
