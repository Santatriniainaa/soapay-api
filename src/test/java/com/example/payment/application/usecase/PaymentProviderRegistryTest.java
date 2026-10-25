package com.example.payment.application.usecase;

import com.example.payment.application.port.out.PaymentProviderPort;
import com.example.payment.domain.enums.PaymentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentProviderRegistryTest {
    @Test
    void registryExposesEachConfiguredStrategy() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(
                adapter(PaymentProvider.MVOLA_MOCK),
                adapter(PaymentProvider.ORANGE_MONEY_MOCK),
                adapter(PaymentProvider.AIRTEL_MONEY_MOCK)));

        assertEquals(3, registry.all().size());
        assertEquals(PaymentProvider.MVOLA_MOCK, registry.get(PaymentProvider.MVOLA_MOCK).provider());
    }

    @Test
    void registryReturnsAnImmutableProviderMap() {
        PaymentProviderRegistry registry = new PaymentProviderRegistry(List.of(
                adapter(PaymentProvider.MVOLA_MOCK),
                adapter(PaymentProvider.ORANGE_MONEY_MOCK),
                adapter(PaymentProvider.AIRTEL_MONEY_MOCK)));

        assertThrows(UnsupportedOperationException.class,
                () -> registry.all().clear());
        assertTrue(registry.all().containsKey(PaymentProvider.AIRTEL_MONEY_MOCK));
    }

    @Test
    void registryRejectsDuplicateStrategies() {
        assertThrows(IllegalStateException.class, () -> new PaymentProviderRegistry(List.of(
                adapter(PaymentProvider.MVOLA_MOCK), adapter(PaymentProvider.MVOLA_MOCK),
                adapter(PaymentProvider.ORANGE_MONEY_MOCK), adapter(PaymentProvider.AIRTEL_MONEY_MOCK))));
    }

    @Test
    void registryRejectsMissingStrategies() {
        assertThrows(IllegalStateException.class, () -> new PaymentProviderRegistry(List.of(
                adapter(PaymentProvider.MVOLA_MOCK), adapter(PaymentProvider.ORANGE_MONEY_MOCK))));
    }

    private static PaymentProviderPort adapter(PaymentProvider provider) {
        PaymentProviderPort adapter = mock(PaymentProviderPort.class);
        when(adapter.provider()).thenReturn(provider);
        return adapter;
    }
}