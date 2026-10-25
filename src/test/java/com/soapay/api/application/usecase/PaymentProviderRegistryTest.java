package com.soapay.api.application.usecase;

import com.soapay.api.application.port.out.ApiProviderPort;
import com.soapay.api.domain.enums.ApiProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiProviderRegistryTest {
    @Test
    void registryExposesEachConfiguredStrategy() {
        ApiProviderRegistry registry = new ApiProviderRegistry(List.of(
                adapter(ApiProvider.MVOLA_MOCK),
                adapter(ApiProvider.ORANGE_MONEY_MOCK),
                adapter(ApiProvider.AIRTEL_MONEY_MOCK)));

        assertEquals(3, registry.all().size());
        assertEquals(ApiProvider.MVOLA_MOCK, registry.get(ApiProvider.MVOLA_MOCK).provider());
    }

    @Test
    void registryReturnsAnImmutableProviderMap() {
        ApiProviderRegistry registry = new ApiProviderRegistry(List.of(
                adapter(ApiProvider.MVOLA_MOCK),
                adapter(ApiProvider.ORANGE_MONEY_MOCK),
                adapter(ApiProvider.AIRTEL_MONEY_MOCK)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().clear());
        assertTrue(registry.all().containsKey(ApiProvider.AIRTEL_MONEY_MOCK));
    }

    @Test
    void registryRejectsDuplicateStrategies() {
        assertThrows(IllegalStateException.class, () -> new ApiProviderRegistry(List.of(
                adapter(ApiProvider.MVOLA_MOCK), adapter(ApiProvider.MVOLA_MOCK),
                adapter(ApiProvider.ORANGE_MONEY_MOCK), adapter(ApiProvider.AIRTEL_MONEY_MOCK))));
    }

    @Test
    void registryRejectsMissingStrategies() {
        assertThrows(IllegalStateException.class, () -> new ApiProviderRegistry(List.of(
                adapter(ApiProvider.MVOLA_MOCK), adapter(ApiProvider.ORANGE_MONEY_MOCK))));
    }

    private static ApiProviderPort adapter(ApiProvider provider) {
        ApiProviderPort adapter = mock(ApiProviderPort.class);
        when(adapter.provider()).thenReturn(provider);
        return adapter;
    }
}