package com.soapay.api.infrastructure.provider.mock;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.SimulationResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockProviderTest {
    @Test
    void mvolaProviderUsesConfiguredSimulationResultAndAvailability() {
        MvolaMockProvider provider = new MvolaMockProvider(SimulationResult.PENDING, false);

        assertEquals(ApiProvider.MVOLA_MOCK, provider.provider());
        assertFalse(provider.isAvailable());
        assertEquals(SimulationResult.PENDING,
                provider.simulateApi("0340000000", new BigDecimal("100.00")));
    }

    @Test
    void mockProviderAcceptsOnlyExpectedDemoPhoneNumberFormat() {
        MvolaMockProvider provider = new MvolaMockProvider(SimulationResult.SUCCESS, true);

        assertTrue(provider.isPhoneNumberValid("0321234567"));
        assertTrue(provider.isPhoneNumberValid("0331234567"));
        assertTrue(provider.isPhoneNumberValid("0341234567"));
        assertFalse(provider.isPhoneNumberValid("0311234567"));
        assertFalse(provider.isPhoneNumberValid("034123456"));
        assertFalse(provider.isPhoneNumberValid(null));
    }
}
