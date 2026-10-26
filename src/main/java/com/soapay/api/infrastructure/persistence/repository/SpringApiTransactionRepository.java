package com.soapay.api.infrastructure.persistence.repository;

import com.soapay.api.infrastructure.persistence.entity.ApiTransactionEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringApiTransactionRepository extends JpaRepository<ApiTransactionEntity, UUID> {
    Optional<ApiTransactionEntity> findByClientIdAndIdempotencyKey(
            String clientId, 
            String idempotencyKey);

    Optional<ApiTransactionEntity> findByReferenceAndClientId(
            String reference, 
            String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ApiTransactionEntity p where p.reference = :reference and p.clientId = :clientId")
    Optional<ApiTransactionEntity> findByReferenceAndClientIdForUpdate(
            @Param("reference") String reference,
            @Param("clientId") String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ApiTransactionEntity p where p.reference = :reference")
    Optional<ApiTransactionEntity> findByReferenceForUpdate(
            @Param("reference") String reference);

    Page<ApiTransactionEntity> findByClientIdOrderByCreatedAtDesc(
            String clientId, 
            Pageable pageable);

    default List<ApiTransactionEntity> findTop100ByClientIdOrderByCreatedAtDesc(
            String clientId) {
        return findByClientIdOrderByCreatedAtDesc(
                clientId, 
                Pageable.ofSize(100)).getContent();
    }
}
