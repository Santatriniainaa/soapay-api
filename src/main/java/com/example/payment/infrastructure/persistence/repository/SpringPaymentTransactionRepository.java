package com.example.payment.infrastructure.persistence.repository;

import com.example.payment.infrastructure.persistence.entity.PaymentTransactionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringPaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, UUID> {
    Optional<PaymentTransactionEntity> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    Optional<PaymentTransactionEntity> findByReferenceAndClientId(String reference, String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransactionEntity p where p.reference = :reference and p.clientId = :clientId")
    Optional<PaymentTransactionEntity> findByReferenceAndClientIdForUpdate(
            @Param("reference") String reference,
            @Param("clientId") String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentTransactionEntity p where p.reference = :reference")
    Optional<PaymentTransactionEntity> findByReferenceForUpdate(@Param("reference") String reference);

    List<PaymentTransactionEntity> findTop100ByClientIdOrderByCreatedAtDesc(String clientId);
}
