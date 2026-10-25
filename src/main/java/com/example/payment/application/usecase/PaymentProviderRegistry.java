package com.example.payment.application.usecase;

import com.example.payment.application.port.out.PaymentProviderPort;
import com.example.payment.domain.enums.PaymentProvider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registry for provider strategies, validated once when the application starts. */
@Component
public class PaymentProviderRegistry {
    private final Map<PaymentProvider, PaymentProviderPort> providers;

    public PaymentProviderRegistry(List<PaymentProviderPort> adapters) {
        EnumMap<PaymentProvider, PaymentProviderPort> registry = new EnumMap<>(PaymentProvider.class);
        for (PaymentProviderPort adapter : adapters) {
            if (registry.putIfAbsent(adapter.provider(), adapter) != null) {
                throw new IllegalStateException("Duplicate adapter for provider " + adapter.provider());
            }
        }
        for (PaymentProvider provider : PaymentProvider.values()) {
            if (!registry.containsKey(provider)) {
                throw new IllegalStateException("Missing adapter for provider " + provider);
            }
        }
        this.providers = Map.copyOf(registry);
    }

    public PaymentProviderPort get(PaymentProvider provider) {
        return providers.get(provider);
    }

    public Map<PaymentProvider, PaymentProviderPort> all() {
        return providers;
    }
}