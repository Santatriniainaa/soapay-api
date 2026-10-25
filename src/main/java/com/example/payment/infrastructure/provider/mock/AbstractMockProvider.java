package com.example.payment.infrastructure.provider.mock;

import com.example.payment.application.port.out.PaymentProviderPort;

import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.SimulationResult;

import java.math.BigDecimal;

abstract class AbstractMockProvider implements PaymentProviderPort {
    private final PaymentProvider provider;
    private final SimulationResult result;
    private final boolean enabled;

    AbstractMockProvider(PaymentProvider provider, SimulationResult result, boolean enabled) {
        this.provider = provider;
        this.result = result;
        this.enabled = enabled;
    }

    @Override
    public PaymentProvider provider() {
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
    public SimulationResult simulatePayment(String phoneNumber, BigDecimal amount) {
        return result;
    }
}
