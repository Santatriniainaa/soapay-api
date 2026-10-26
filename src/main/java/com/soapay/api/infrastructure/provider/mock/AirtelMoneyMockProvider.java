package com.soapay.api.infrastructure.provider.mock;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.SimulationResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AirtelMoneyMockProvider extends AbstractMockProvider {
    public AirtelMoneyMockProvider(
            @Value("${api.simulation.result:SUCCESS}") SimulationResult result,
            @Value("${api.simulation.enabled:true}") boolean enabled) {
        super(
            ApiProvider.AIRTEL_MONEY_MOCK, 
            result, 
            enabled);
    }
}
