package com.soapay.api.infrastructure.provider.mock;

import com.soapay.api.application.port.out.ApiProviderPort;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.SimulationResult;

import java.math.BigDecimal;

abstract class AbstractMockProvider implements ApiProviderPort {
    private final ApiProvider provider;
    private final SimulationResult result;
    private final boolean enabled;

    AbstractMockProvider(ApiProvider provider, SimulationResult result, boolean enabled) {
        this.provider = provider;
        this.result = result;
        this.enabled = enabled;
    }

    @Override
    public ApiProvider provider() {
        return provider;
    }

    @Override
    public boolean isAvailable() {
        return enabled;
    }

    @Override
    public boolean isPhoneNumberValid(String phoneNumber) {
        return phoneNumber != null && phoneNumber.matches("03[234][0-9]{7}");
    }

    @Override
    public SimulationResult simulateApi(String phoneNumber, BigDecimal amount) {
        return result;
    }
}
