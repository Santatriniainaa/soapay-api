package com.example.payment.application.port.out;

import com.example.payment.domain.model.PaymentRefund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRefundStore {
    Optional<PaymentRefund> findByClientAndKey(String clientId, String key);

    Optional<PaymentRefund> findByReferenceAndClient(String reference, String clientId);

    List<PaymentRefund> findByPaymentAndClient(String paymentReference, String clientId);

    List<PaymentRefund> findByPaymentAndClientForUpdate(String paymentReference, String clientId);

    BigDecimal sumRefunded(String paymentReference);

    PaymentRefund save(PaymentRefund refund);
}
