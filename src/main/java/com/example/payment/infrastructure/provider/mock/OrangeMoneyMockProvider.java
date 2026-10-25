package com.example.payment.infrastructure.provider.mock;

import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.SimulationResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrangeMoneyMockProvider extends AbstractMockProvider {
    public OrangeMoneyMockProvider(@Value("${payment.simulation.result:SUCCESS}") SimulationResult result,
                                   @Value("${payment.simulation.enabled:true}") boolean enabled) {
        super(PaymentProvider.ORANGE_MONEY_MOCK, result, enabled);
    }
}
