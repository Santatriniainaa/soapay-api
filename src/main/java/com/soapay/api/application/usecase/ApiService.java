package com.soapay.api.application.usecase;

import com.soapay.api.application.dto.request.CreateApiRequest;
import com.soapay.api.application.dto.response.ApiResponse;
import com.soapay.api.application.port.out.ApiTransactionStore;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.exception.ApiException;
import com.soapay.api.domain.model.ApiTransaction;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApiService {
    private final ApiTransactionStore store;
    private final ApiCreationService creationService;

    public ApiService(
            ApiTransactionStore store, 
            ApiCreationService creationService) {
        this.store = store;
        this.creationService = creationService;
    }

    public ApiResponse create(
            CreateApiRequest request, 
            String key, 
            String clientId) {
        return creationService.create(
            request, 
            key, 
            clientId);
    }

    @Transactional(readOnly = true)
    public ApiResponse get(
            String reference, 
            String clientId) {
        return store
            .findByReferenceAndClient(reference, clientId)
            .map(ApiService::toResponse)
            .orElseThrow(() -> new ApiException(
                    "TRANSACTION_NOT_FOUND", 
                    "Transaction introuvable", 
                    404));
    }

    @Transactional(readOnly = true)
    public List<ApiResponse> list(String clientId) {
        return list(clientId, 0, 100);
    }

    @Transactional(readOnly = true)
    public List<ApiResponse> list(String clientId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        return store
            .findRecentByClient(
                clientId, 
                PageRequest.of(safePage, Math.min(safeSize, 100)))
            .stream()
            .map(ApiService::toResponse)
            .toList();
    }

    @Transactional
    public ApiResponse cancel(String reference, String clientId) {
        ApiTransaction tx = store
            .findByReferenceAndClientForUpdate(reference, clientId)
            .orElseThrow(() -> new ApiException(
                "TRANSACTION_NOT_FOUND", 
                "Transaction introuvable", 
                404));
        return toResponse(
            store.save(tx.transitionTo(
                ApiStatus.CANCELLED, 
                null, 
                null)));
    }

    @Transactional
    public ApiResponse confirm(String reference) {
        ApiTransaction tx = store
            .findByReferenceForUpdate(reference)
            .orElseThrow(() -> new ApiException(
                "TRANSACTION_NOT_FOUND", 
                "Transaction introuvable", 
                404));
        if (tx.status() != ApiStatus.PENDING) {
            throw new ApiException(
                "INVALID_STATE_TRANSITION", 
                "Seule une transaction en attente peut être confirmée", 
                409);
        }
        return toResponse(store
            .save(tx.transitionTo(
                ApiStatus.SUCCESS, 
                null, 
                null)));
    }

    private static ApiResponse toResponse(ApiTransaction tx) {
        String message = switch (tx.status()) {
            case SUCCESS -> "Paiement simulé accepté";
            case PENDING -> "Paiement simulé en attente de confirmation";
            case FAILED -> tx.failureMessage() == null ? "Paiement simulé refusé" : tx.failureMessage();
            case CANCELLED -> "Paiement simulé annulé";
            default -> "État de paiement simulé";
        };
        return new ApiResponse(
            tx.reference(),
            tx.provider(),
            tx.amount(),
            tx.currency(),
            tx.status(),
            true,
            message,
            tx.createdAt(),
            tx.updatedAt());
    }
}
