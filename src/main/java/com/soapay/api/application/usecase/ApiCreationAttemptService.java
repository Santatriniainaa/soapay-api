package com.soapay.api.application.usecase;

import com.soapay.api.application.dto.request.CreateApiRequest;
import com.soapay.api.application.port.out.ApiProviderPort;
import com.soapay.api.application.port.out.ApiTransactionStore;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.enums.SimulationResult;
import com.soapay.api.domain.model.ApiTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiCreationAttemptService {
    private final ApiTransactionStore store;

    public ApiCreationAttemptService(ApiTransactionStore store) {
        this.store = store;
    }

    @Transactional
    public ApiTransaction execute(
            CreateApiRequest request,
            String key,
            String clientId,
            ApiProviderPort provider) {
        var existing = store.findByClientAndKey(clientId, key);
        if (existing.isPresent()) {
            return existing.get();
        }

        ApiTransaction tx = ApiTransaction.create(
                key,
                clientId,
                request.provider(),
                request.phoneNumber(),
                request.amount(),
                request.currency(),
                request.description());
        tx = store.saveAndFlush(tx);
        SimulationResult result = provider.simulateApi(request.phoneNumber(), request.amount());
        tx = switch (result) {
            case SUCCESS -> tx.transitionTo(ApiStatus.SUCCESS, null, null);
            case PENDING -> tx.transitionTo(ApiStatus.PENDING, null, null);
            case FAILED -> tx.transitionTo(
                    ApiStatus.FAILED, "SIMULATED_DECLINE", "Refus simulé par le fournisseur");
            case TIMEOUT -> tx.transitionTo(
                    ApiStatus.FAILED, "SIMULATED_TIMEOUT", "Délai simulé dépassé");
        };
        return store.save(tx);
    }
}
