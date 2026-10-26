package com.soapay.api.infrastructure.persistence.adapter;

import com.soapay.api.application.port.out.ApiRefundStore;
import com.soapay.api.domain.enums.RefundStatus;
import com.soapay.api.domain.model.ApiRefund;
import com.soapay.api.infrastructure.persistence.entity.ApiRefundEntity;
import com.soapay.api.infrastructure.persistence.repository.SpringApiRefundRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class JpaApiRefundAdapter implements ApiRefundStore {
    private final SpringApiRefundRepository repository;

    public JpaApiRefundAdapter(
            SpringApiRefundRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ApiRefund> findByClientAndKey(
            String clientId, 
            String key) {
        return repository
                .findByClientIdAndIdempotencyKey(
                    clientId, 
                    key)
                .map(JpaApiRefundAdapter::toDomain);
    }

    @Override
    public Optional<ApiRefund> findByReferenceAndClient(
            String reference, 
            String clientId) {
        return repository
                .findByReferenceAndClientId(
                    reference, 
                    clientId)
                .map(JpaApiRefundAdapter::toDomain);
    }

    @Override
    public List<ApiRefund> findByApiAndClient(
            String apiReference, 
            String clientId) {
        return findByApiAndClient(
                apiReference, 
                clientId, 
                PageRequest.of(
                    0, 
                    100));
    }

    @Override
    public List<ApiRefund> findByApiAndClient(
            String apiReference, 
            String clientId, 
            Pageable pageable) {
        return repository
                .findByTransactionReferenceAndClientIdOrderByCreatedAtDesc(
                    apiReference, 
                    clientId, 
                    pageable)
                .stream()
                .map(JpaApiRefundAdapter::toDomain)
                .toList();
    }

    @Override
    public List<ApiRefund> findByApiAndClientForUpdate(
            String apiReference, 
            String clientId) {
        return repository
                .findByApiAndClientForUpdate(
                    apiReference, 
                    clientId)
                .stream()
                .map(JpaApiRefundAdapter::toDomain)
                .toList();
    }

    @Override
    public BigDecimal sumRefunded(
            String apiReference) {
        BigDecimal total = repository
                .sumByTransactionReferenceAndStatus(
                    apiReference, 
                    RefundStatus.REFUNDED);
        return total == null 
                ? BigDecimal.ZERO 
                : total;
    }

    @Override
    public ApiRefund save(
            ApiRefund refund) {
        ApiRefundEntity entity = repository
                .findById(refund.id())
                .orElseGet(ApiRefundEntity::new);
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
        ApiRefundEntity saved = repository.save(entity);
        return toDomain(repository.saveAndFlush(saved));
    }

    private static ApiRefund toDomain(ApiRefundEntity entity) {
        return new ApiRefund(
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
