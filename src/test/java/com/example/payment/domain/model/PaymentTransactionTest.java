package com.example.payment.domain.model;

import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.PaymentStatus;
import com.example.payment.domain.exception.PaymentException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentTransactionTest {
    @Test
    void successfulPaymentCannotBeCancelled() {
        PaymentTransaction transaction = PaymentTransaction.create("key-1", "client", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.SUCCESS, null, null);

        assertThrows(PaymentException.class, () -> transaction.transitionTo(PaymentStatus.CANCELLED, null, null));
    }

    @Test
    void pendingPaymentCanBeConfirmed() {
        PaymentTransaction transaction = PaymentTransaction.create("key-1", "client", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.PENDING, null, null)
                .transitionTo(PaymentStatus.SUCCESS, null, null);

        assertEquals(PaymentStatus.SUCCESS, transaction.status());
        assertEquals(new BigDecimal("100.00"), transaction.amount());
    }

    @Test
    void successfulAndPartiallyRefundedPaymentsRemainRefundable() {
        assertFalse(PaymentStatus.SUCCESS.isTerminal());
        assertFalse(PaymentStatus.PARTIALLY_REFUNDED.isTerminal());
        assertTrue(PaymentStatus.REFUNDED.isTerminal());
        assertTrue(PaymentStatus.FAILED.isTerminal());
    }

    @Test
    void failedPaymentCannotTransitionAgain() {
        PaymentTransaction failed = PaymentTransaction.create("key-1", "client", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.FAILED, "DECLINED", "Declined");

        PaymentException exception = assertThrows(PaymentException.class,
                () -> failed.transitionTo(PaymentStatus.SUCCESS, null, null));

        assertEquals("INVALID_STATE_TRANSITION", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        assertEquals("DECLINED", failed.failureCode());
        assertNotNull(failed.completedAt());
    }

    @Test
    void pendingTransitionDoesNotCompletePayment() {
        PaymentTransaction created = PaymentTransaction.create("key-1", "client", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null);

        PaymentTransaction pending = created.transitionTo(PaymentStatus.PENDING, null, null);

        assertEquals(PaymentStatus.PENDING, pending.status());
        assertEquals(created.createdAt(), pending.createdAt());
        assertTrue(pending.completedAt() == null);
        assertNotNull(pending.updatedAt());
    }

    @Test
    void successfulPaymentCanTransitionThroughRefundPendingToPartialRefund() {
        PaymentTransaction transaction = PaymentTransaction.create("key-1", "client", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.SUCCESS, null, null)
                .transitionTo(PaymentStatus.REFUND_PENDING, null, null)
                .transitionTo(PaymentStatus.PARTIALLY_REFUNDED, null, null);

        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, transaction.status());
        assertThrows(PaymentException.class,
                () -> transaction.transitionTo(PaymentStatus.SUCCESS, null, null));
    }
}
