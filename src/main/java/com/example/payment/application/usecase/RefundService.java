package com.example.payment.application.usecase;

import com.example.payment.application.dto.request.CreateRefundRequest;
import com.example.payment.application.dto.response.RefundResponse;
import com.example.payment.application.port.out.PaymentRefundStore;
import com.example.payment.application.port.out.PaymentTransactionStore;

import com.example.payment.domain.enums.PaymentStatus;
import com.example.payment.domain.exception.PaymentException;
import com.example.payment.domain.model.PaymentRefund;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class RefundService {
    private final PaymentTransactionStore transactions;
    private final PaymentRefundStore refunds;

    public RefundService(PaymentTransactionStore transactions, PaymentRefundStore refunds) {
        this.transactions = transactions; this.refunds = refunds;
    }

    @Transactional
    public RefundResponse create(String paymentReference, CreateRefundRequest request, String key, String clientId) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new PaymentException("IDEMPOTENCY_KEY_REQUIRED", "L'en-tête Idempotency-Key est requis (128 caractères maximum)", 400);
        }

        var existing = findMatchingRequest(paymentReference, request, key, clientId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        var payment = transactions.findByReferenceAndClientForUpdate(paymentReference, clientId)
                .orElseThrow(() -> new PaymentException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404));

        // Recheck under the payment lock: another request with this key may have
        // completed while this request waited for the lock.
        existing = findMatchingRequest(paymentReference, request, key, clientId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // The payment row lock serializes distinct refund keys for this transaction.
        if (payment.status() != PaymentStatus.SUCCESS && payment.status() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentException("REFUND_NOT_ALLOWED", "Seule une transaction réussie peut être remboursée", 409);
        }

        BigDecimal alreadyRefunded = refunds.sumRefunded(paymentReference);
        BigDecimal remaining = payment.amount().subtract(alreadyRefunded);
        if (request.amount().compareTo(remaining) > 0) {
            throw new PaymentException("REFUND_EXCEEDS_REMAINING", "Le remboursement dépasse le solde remboursable", 422);
        }

        PaymentRefund refund = PaymentRefund.completed(key, clientId, paymentReference, request.amount(), request.reason());
        refunds.save(refund);

        BigDecimal after = alreadyRefunded.add(request.amount());
        PaymentStatus next = after.compareTo(payment.amount()) == 0
                ? PaymentStatus.REFUNDED
                : PaymentStatus.PARTIALLY_REFUNDED;
        transactions.save(payment.transitionTo(PaymentStatus.REFUND_PENDING, null, null)
                .transitionTo(next, null, null));
        return toResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> list(String paymentReference, String clientId) {
        if (transactions.findByReferenceAndClient(paymentReference, clientId).isEmpty())
            throw new PaymentException("TRANSACTION_NOT_FOUND", "Transaction introuvable", 404);
        return refunds.findByPaymentAndClient(paymentReference, clientId).stream().map(RefundService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RefundResponse get(String reference, String clientId) {
        return refunds.findByReferenceAndClient(reference, clientId).map(RefundService::toResponse)
                .orElseThrow(() -> new PaymentException("REFUND_NOT_FOUND", "Remboursement introuvable", 404));
    }

    private static RefundResponse toResponse(PaymentRefund refund) {
        return new RefundResponse(refund.reference(), refund.transactionReference(), refund.amount(), refund.status(),
                true, refund.reason(), refund.createdAt(), refund.processedAt());
    }

    private java.util.Optional<PaymentRefund> findMatchingRequest(
            String paymentReference, CreateRefundRequest request, String key, String clientId) {
        var existing = refunds.findByClientAndKey(clientId, key);
        if (existing.isEmpty()) {
            return existing;
        }

        PaymentRefund prior = existing.get();
        boolean sameRequest = prior.transactionReference().equals(paymentReference)
                && prior.amount().compareTo(request.amount()) == 0
                && Objects.equals(prior.reason(), request.reason());
        if (!sameRequest) {
            throw new PaymentException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Cette clé a déjà été utilisée avec un contenu différent",
                    409);
        }
        return existing;
    }
}
