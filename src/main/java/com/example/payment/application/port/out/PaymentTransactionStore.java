package com.example.payment.application.port.out;

import com.example.payment.domain.model.PaymentTransaction;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionStore {
    Optional<PaymentTransaction> findByClientAndKey(String clientId, String key);

    Optional<PaymentTransaction> findByReferenceAndClient(String reference, String clientId);

    Optional<PaymentTransaction> findByReferenceAndClientForUpdate(String reference, String clientId);

    Optional<PaymentTransaction> findByReferenceForUpdate(String reference);

    List<PaymentTransaction> findRecentByClient(String clientId);

    PaymentTransaction save(PaymentTransaction transaction);
}
