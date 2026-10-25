package com.example.payment.infrastructure.persistence.adapter;

import com.example.payment.application.port.out.PaymentTransactionStore;

import com.example.payment.domain.model.PaymentTransaction;

import com.example.payment.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.example.payment.infrastructure.persistence.repository.SpringPaymentTransactionRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class JpaPaymentTransactionAdapter implements PaymentTransactionStore {
    private final SpringPaymentTransactionRepository repository;

    public JpaPaymentTransactionAdapter(SpringPaymentTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PaymentTransaction> findByClientAndKey(String clientId, String key) {
        return repository.findByClientIdAndIdempotencyKey(clientId, key).map(JpaPaymentTransactionAdapter::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByReferenceAndClient(String reference, String clientId) {
        return repository.findByReferenceAndClientId(reference, clientId).map(JpaPaymentTransactionAdapter::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByReferenceAndClientForUpdate(String reference, String clientId) {
        return repository.findByReferenceAndClientIdForUpdate(reference, clientId).map(JpaPaymentTransactionAdapter::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByReferenceForUpdate(String reference) {
        return repository.findByReferenceForUpdate(reference).map(JpaPaymentTransactionAdapter::toDomain);
    }

    @Override
    public List<PaymentTransaction> findRecentByClient(String clientId) {
        return repository.findTop100ByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(JpaPaymentTransactionAdapter::toDomain).toList();
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        PaymentTransactionEntity entity = repository.findById(Objects.requireNonNull(transaction.id()))
            .orElseGet(PaymentTransactionEntity::new);
        entity.setId(transaction.id());
        entity.setReference(transaction.reference());
        entity.setIdempotencyKey(transaction.idempotencyKey());
        entity.setClientId(transaction.clientId());
        entity.setProvider(transaction.provider());
        entity.setPhoneNumber(transaction.phoneNumber());
        entity.setAmount(transaction.amount());
        entity.setCurrency(transaction.currency());
        entity.setStatus(transaction.status());
        entity.setDescription(transaction.description());
        entity.setFailureCode(transaction.failureCode());
        entity.setFailureMessage(transaction.failureMessage());
        entity.setCreatedAt(transaction.createdAt());
        entity.setUpdatedAt(transaction.updatedAt());
        entity.setCompletedAt(transaction.completedAt());
        return toDomain(repository.save(entity));
    }

    private static PaymentTransaction toDomain(PaymentTransactionEntity entity) {
        return new PaymentTransaction(entity.getId(), entity.getReference(), entity.getIdempotencyKey(),
                entity.getClientId(), entity.getProvider(), entity.getPhoneNumber(), entity.getAmount(),
                entity.getCurrency(), entity.getStatus(), entity.getDescription(), entity.getFailureCode(),
                entity.getFailureMessage(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCompletedAt());
    }
}
