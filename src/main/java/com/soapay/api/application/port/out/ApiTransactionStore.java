package com.soapay.api.application.port.out;

import com.soapay.api.domain.model.ApiTransaction;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ApiTransactionStore {
    Optional<ApiTransaction> findByClientAndKey(String clientId, String key);

    Optional<ApiTransaction> findByReferenceAndClient(String reference, String clientId);

    Optional<ApiTransaction> findByReferenceAndClientForUpdate(String reference, String clientId);

    Optional<ApiTransaction> findByReferenceForUpdate(String reference);

    List<ApiTransaction> findRecentByClient(String clientId);

    List<ApiTransaction> findRecentByClient(String clientId, Pageable pageable);

    ApiTransaction save(ApiTransaction transaction);

    ApiTransaction saveAndFlush(ApiTransaction transaction);
}
