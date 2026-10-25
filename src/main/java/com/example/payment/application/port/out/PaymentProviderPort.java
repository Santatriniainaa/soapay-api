package com.example.payment.application.port.out;

import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.SimulationResult;

import java.math.BigDecimal;

public interface PaymentProviderPort {
    PaymentProvider provider();
    boolean isAvailable();
    boolean isPhoneNumberValid(String phoneNumber);
    SimulationResult simulatePayment(String phoneNumber, BigDecimal amount);
}
