package com.example.payment.infrastructure.persistence.adapter;

import com.example.payment.application.port.out.PaymentRefundStore;
import com.example.payment.domain.model.PaymentRefund;
import com.example.payment.infrastructure.persistence.entity.PaymentRefundEntity;
import com.example.payment.infrastructure.persistence.repository.SpringPaymentRefundRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class JpaPaymentRefundAdapter implements PaymentRefundStore {
    private final SpringPaymentRefundRepository repository;

    public JpaPaymentRefundAdapter(SpringPaymentRefundRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentRefund> findByClientAndKey(String clientId, String key) {
        return repository.findByClientIdAndIdempotencyKey(clientId, key)
                .map(JpaPaymentRefundAdapter::toDomain);
    }

    @Override
    public Optional<PaymentRefund> findByReferenceAndClient(String reference, String clientId) {
        return repository.findByReferenceAndClientId(reference, clientId)
                .map(JpaPaymentRefundAdapter::toDomain);
    }

    @Override
    public List<PaymentRefund> findByPaymentAndClient(String paymentReference, String clientId) {
        return repository.findByTransactionReferenceAndClientIdOrderByCreatedAtDesc(paymentReference, clientId)
                .stream()
                .map(JpaPaymentRefundAdapter::toDomain)
                .toList();
    }

    @Override
    public List<PaymentRefund> findByPaymentAndClientForUpdate(String paymentReference, String clientId) {
        return repository.findByPaymentAndClientForUpdate(paymentReference, clientId)
                .stream()
                .map(JpaPaymentRefundAdapter::toDomain)
                .toList();
    }

    @Override
    public BigDecimal sumRefunded(String paymentReference) {
        BigDecimal total = repository.sumRefunded(paymentReference);
        return total == null ? BigDecimal.ZERO : total;
    }

    @Override
    public PaymentRefund save(PaymentRefund refund) {
        PaymentRefundEntity entity = new PaymentRefundEntity();
        entity.setId(refund.id());
        entity.setReference(refund.reference());
        entity.setIdempotencyKey(refund.idempotencyKey());
        entity.setClientId(refund.clientId());
        entity.setTransactionReference(refund.transactionReference());
        entity.setAmount(refund.amount());
        entity.setStatus(refund.status());
        entity.setReason(refund.reason());
        entity.setCreatedAt(refund.createdAt());
        entity.setProcessedAt(refund.processedAt());
        return toDomain(repository.save(entity));
    }

    private static PaymentRefund toDomain(PaymentRefundEntity entity) {
        return new PaymentRefund(
                entity.getId(),
                entity.getReference(),
                entity.getIdempotencyKey(),
                entity.getClientId(),
                entity.getTransactionReference(),
                entity.getAmount(),
                entity.getStatus(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getProcessedAt());
    }
}
