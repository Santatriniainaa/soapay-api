package com.soapay.api.application.port.out;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.SimulationResult;

import java.math.BigDecimal;

public interface ApiProviderPort {
    ApiProvider provider();
    boolean isAvailable();
    boolean isPhoneNumberValid(String phoneNumber);
    SimulationResult simulateApi(String phoneNumber, BigDecimal amount);
}
