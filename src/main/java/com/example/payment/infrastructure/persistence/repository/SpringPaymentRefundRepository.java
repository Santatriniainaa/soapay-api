package com.example.payment.infrastructure.persistence.repository;

import com.example.payment.infrastructure.persistence.entity.PaymentRefundEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringPaymentRefundRepository extends JpaRepository<PaymentRefundEntity, UUID> {
    Optional<PaymentRefundEntity> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    Optional<PaymentRefundEntity> findByReferenceAndClientId(String reference, String clientId);

    List<PaymentRefundEntity> findByTransactionReferenceAndClientIdOrderByCreatedAtDesc(
        String transactionReference,
        String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefundEntity r "
        + "where r.transactionReference = :reference and r.clientId = :clientId "
        + "order by r.createdAt desc")
    List<PaymentRefundEntity> findByPaymentAndClientForUpdate(
        @Param("reference") String reference,
        @Param("clientId") String clientId);

    @Query("select coalesce(sum(r.amount), 0) from PaymentRefundEntity r "
        + "where r.transactionReference = :reference and r.status = 'REFUNDED'")
    BigDecimal sumRefunded(@Param("reference") String reference);
}
