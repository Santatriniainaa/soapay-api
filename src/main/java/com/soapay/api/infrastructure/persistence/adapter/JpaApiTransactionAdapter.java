package com.soapay.api.infrastructure.persistence.adapter;

import com.soapay.api.application.port.out.ApiTransactionStore;

import com.soapay.api.domain.model.ApiTransaction;

import com.soapay.api.infrastructure.persistence.entity.ApiTransactionEntity;
import com.soapay.api.infrastructure.persistence.repository.SpringApiTransactionRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class JpaApiTransactionAdapter implements ApiTransactionStore {
    private final SpringApiTransactionRepository repository;

    public JpaApiTransactionAdapter(
            SpringApiTransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ApiTransaction> findByClientAndKey(
            String clientId, 
            String key) {
        return repository
                .findByClientIdAndIdempotencyKey(clientId, key)
                .map(JpaApiTransactionAdapter::toDomain);
    }

    @Override
    public Optional<ApiTransaction> findByReferenceAndClient(
            String reference, 
            String clientId) {
        return repository
                .findByReferenceAndClientId(
                    reference, 
                    clientId)
                .map(JpaApiTransactionAdapter::toDomain);
    }

    @Override
    public Optional<ApiTransaction> findByReferenceAndClientForUpdate(
            String reference, 
            String clientId) {
        return repository
                .findByReferenceAndClientIdForUpdate(
                    reference, 
                    clientId)
                .map(JpaApiTransactionAdapter::toDomain);
    }

    @Override
    public Optional<ApiTransaction> findByReferenceForUpdate(
            String reference) {
        return repository
                .findByReferenceForUpdate(reference)
                .map(JpaApiTransactionAdapter::toDomain);
    }

    @Override
    public List<ApiTransaction> findRecentByClient(
            String clientId) {
        return findRecentByClient(
                clientId, 
                PageRequest.of(
                    0, 
                    100));
    }

    @Override
    public List<ApiTransaction> findRecentByClient(
            String clientId, 
            Pageable pageable) {
        return repository
                .findByClientIdOrderByCreatedAtDesc(
                    clientId, 
                    pageable)
                .stream()
                .map(JpaApiTransactionAdapter::toDomain)
                .toList();
    }

    @Override
    public ApiTransaction save(
            ApiTransaction transaction) {
        return persist(
                transaction, 
                false);
    }

    @Override
    public ApiTransaction saveAndFlush(
            ApiTransaction transaction) {
        return persist(
                transaction, 
                true);
    }

    private ApiTransaction persist(
            ApiTransaction transaction, 
            boolean flush) {
        ApiTransactionEntity entity = repository
                .findById(Objects.requireNonNull(transaction.id()))
                .orElseGet(ApiTransactionEntity::new);
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
        return toDomain(flush 
                ? repository.saveAndFlush(entity) 
                : repository.save(entity));
    }

    private static ApiTransaction toDomain(
            ApiTransactionEntity entity) {
        return new ApiTransaction(
                entity.getId(), 
                entity.getReference(), 
                entity.getIdempotencyKey(),
                entity.getClientId(), 
                entity.getProvider(), 
                entity.getPhoneNumber(), 
                entity.getAmount(),
                entity.getCurrency(), 
                entity.getStatus(), 
                entity.getDescription(), 
                entity.getFailureCode(),
                entity.getFailureMessage(), 
                entity.getCreatedAt(), 
                entity.getUpdatedAt(), 
                entity.getCompletedAt());
    }
}
