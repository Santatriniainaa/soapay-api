package com.soapay.api.application.port.out;

import com.soapay.api.domain.model.ApiRefund;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ApiRefundStore {
    Optional<ApiRefund> findByClientAndKey(String clientId, String key);

    Optional<ApiRefund> findByReferenceAndClient(String reference, String clientId);

    List<ApiRefund> findByApiAndClient(String apiReference, String clientId);

    List<ApiRefund> findByApiAndClient(String apiReference, String clientId, Pageable pageable);

    List<ApiRefund> findByApiAndClientForUpdate(String apiReference, String clientId);

    BigDecimal sumRefunded(String apiReference);

    ApiRefund save(ApiRefund refund);
}
