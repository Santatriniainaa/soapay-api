package com.soapay.api.application.usecase;

import com.soapay.api.application.port.out.ApiProviderPort;
import com.soapay.api.domain.enums.ApiProvider;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Registry for provider strategies, validated once when the application starts. */
@Component
public class ApiProviderRegistry {
    private final Map<ApiProvider, ApiProviderPort> providers;

    public ApiProviderRegistry(List<ApiProviderPort> adapters) {
        EnumMap<ApiProvider, ApiProviderPort> registry = new EnumMap<>(ApiProvider.class);
        for (ApiProviderPort adapter : adapters) {
            if (registry.putIfAbsent(adapter.provider(), adapter) != null) {
                throw new IllegalStateException("Duplicate adapter for provider " + adapter.provider());
            }
        }
        for (ApiProvider provider : ApiProvider.values()) {
            if (!registry.containsKey(provider)) {
                throw new IllegalStateException("Missing adapter for provider " + provider);
            }
        }
        this.providers = Map.copyOf(registry);
    }

    public ApiProviderPort get(ApiProvider provider) {
        return providers.get(provider);
    }

    public Map<ApiProvider, ApiProviderPort> all() {
        return providers;
    }
}