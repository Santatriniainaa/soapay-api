package com.soapay.api.infrastructure.persistence.repository;

import com.soapay.api.infrastructure.persistence.entity.ApiRefundEntity;
import com.soapay.api.domain.enums.RefundStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringApiRefundRepository extends JpaRepository<ApiRefundEntity, UUID> {
    Optional<ApiRefundEntity> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    Optional<ApiRefundEntity> findByReferenceAndClientId(String reference, String clientId);

    Page<ApiRefundEntity> findByTransactionReferenceAndClientIdOrderByCreatedAtDesc(
        String transactionReference,
        String clientId,
        Pageable pageable);

    default List<ApiRefundEntity> findByTransactionReferenceAndClientIdOrderByCreatedAtDesc(
        String transactionReference,
        String clientId) {
        return findByTransactionReferenceAndClientIdOrderByCreatedAtDesc(transactionReference, clientId, Pageable.ofSize(100)).getContent();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApiRefundEntity r "
        + "where r.transactionReference = :reference and r.clientId = :clientId "
        + "order by r.createdAt desc")
    List<ApiRefundEntity> findByApiAndClientForUpdate(
        @Param("reference") String reference,
        @Param("clientId") String clientId);

    @Query("select coalesce(sum(r.amount), 0) from ApiRefundEntity r "
        + "where r.transactionReference = :reference and r.status = :status")
    BigDecimal sumByTransactionReferenceAndStatus(
            @Param("reference") String reference,
            @Param("status") RefundStatus status);
}
