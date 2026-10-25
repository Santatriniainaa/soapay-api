package com.example.payment.application.usecase;

import com.example.payment.application.dto.request.CreateRefundRequest;
import com.example.payment.application.port.out.PaymentRefundStore;
import com.example.payment.application.port.out.PaymentTransactionStore;
import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.PaymentStatus;
import com.example.payment.domain.exception.PaymentException;
import com.example.payment.domain.model.PaymentRefund;
import com.example.payment.domain.model.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTest {
    private PaymentTransactionStore transactions;
    private PaymentRefundStore refunds;
    private RefundService service;
    private PaymentTransaction payment;

    @BeforeEach
    void setUp() {
        transactions = mock(PaymentTransactionStore.class);
        refunds = mock(PaymentRefundStore.class);
        service = new RefundService(transactions, refunds);
        payment = PaymentTransaction.create("payment-key", "client-1", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.SUCCESS, null, null);

        when(refunds.findByClientAndKey(any(), any())).thenReturn(Optional.empty());
        when(transactions.findByReferenceAndClientForUpdate(payment.reference(), "client-1"))
                .thenReturn(Optional.of(payment));
        when(refunds.sumRefunded(payment.reference())).thenReturn(BigDecimal.ZERO);
        when(refunds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void partialRefundPersistsRefundAndUpdatesPaymentStatus() {
        var response = service.create(payment.reference(), request("40.00"), "refund-key", "client-1");

        assertEquals(new BigDecimal("40.00"), response.amount());
        assertTrue(response.simulated());
        var transactionCaptor = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, transactionCaptor.getValue().status());
        verify(refunds).save(any(PaymentRefund.class));
    }

    @Test
    void fullRefundMovesPaymentToRefunded() {
        service.create(payment.reference(), request("100.00"), "refund-key", "client-1");

        var transactionCaptor = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        assertEquals(PaymentStatus.REFUNDED, transactionCaptor.getValue().status());
    }

    @Test
    void refundAboveRemainingBalanceIsRejectedWithoutSaving() {
        when(refunds.sumRefunded(payment.reference())).thenReturn(new BigDecimal("80.00"));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.create(payment.reference(), request("20.01"), "refund-key", "client-1"));

        assertEquals("REFUND_EXCEEDS_REMAINING", exception.getCode());
        assertEquals(422, exception.getHttpStatus());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void pendingPaymentCannotBeRefunded() {
        PaymentTransaction pendingPayment = PaymentTransaction.create("payment-key", "client-1",
                PaymentProvider.MVOLA_MOCK, "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.PENDING, null, null);
        when(transactions.findByReferenceAndClientForUpdate(pendingPayment.reference(), "client-1"))
                .thenReturn(Optional.of(pendingPayment));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.create(pendingPayment.reference(), request("10.00"), "refund-key", "client-1"));

        assertEquals("REFUND_NOT_ALLOWED", exception.getCode());
        verify(refunds, never()).save(any());
    }

    @Test
    void repeatedIdenticalRefundReturnsPriorRefundWithoutSavingAgain() {
        PaymentRefund existing = PaymentRefund.completed("refund-key", "client-1", payment.reference(),
                new BigDecimal("25.00"), "duplicate");
        when(refunds.findByClientAndKey("client-1", "refund-key")).thenReturn(Optional.of(existing));

        var response = service.create(payment.reference(), request("25.00", "duplicate"), "refund-key", "client-1");

        assertEquals(existing.reference(), response.refundReference());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void missingPaymentReturnsNotFound() {
        when(transactions.findByReferenceAndClientForUpdate("PAY-missing", "client-1"))
                .thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.create("PAY-missing", request("10.00"), "refund-key", "client-1"));

        assertEquals("TRANSACTION_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getHttpStatus());
        verify(refunds, never()).save(any());
    }

    @Test
    void refundListRequiresPaymentOwnership() {
        when(transactions.findByReferenceAndClient("PAY-missing", "client-1")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.list("PAY-missing", "client-1"));

        assertEquals("TRANSACTION_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getHttpStatus());
    }

    private static CreateRefundRequest request(String amount) {
        return request(amount, "Remboursement test");
    }

    private static CreateRefundRequest request(String amount, String reason) {
        return new CreateRefundRequest(new BigDecimal(amount), reason);
    }
}
