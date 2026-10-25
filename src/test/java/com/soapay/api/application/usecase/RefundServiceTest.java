package com.soapay.api.application.usecase;

import com.soapay.api.application.dto.request.CreateRefundRequest;
import com.soapay.api.application.port.out.ApiRefundStore;
import com.soapay.api.application.port.out.ApiTransactionStore;
import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.enums.RefundStatus;
import com.soapay.api.domain.exception.ApiException;
import com.soapay.api.domain.model.ApiRefund;
import com.soapay.api.domain.model.ApiTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTest {
    private ApiTransactionStore transactions;
    private ApiRefundStore refunds;
    private RefundService service;
    private ApiTransaction api;

    @BeforeEach
    void setUp() {
        transactions = mock(ApiTransactionStore.class);
        refunds = mock(ApiRefundStore.class);
        service = new RefundService(transactions, refunds);
        api = ApiTransaction.create("api-key", "client-1", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.SUCCESS, null, null);

        when(refunds.findByClientAndKey(any(), any())).thenReturn(Optional.empty());
        when(transactions.findByReferenceAndClientForUpdate(api.reference(), "client-1"))
                .thenReturn(Optional.of(api));
        when(refunds.sumRefunded(api.reference())).thenReturn(BigDecimal.ZERO);
        when(refunds.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void partialRefundPersistsRefundAndUpdatesApiStatus() {
        var response = service.create(api.reference(), request("40.00"), "refund-key", "client-1");

        assertEquals(new BigDecimal("40.00"), response.amount());
        assertTrue(response.simulated());
        var transactionCaptor = org.mockito.ArgumentCaptor.forClass(ApiTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        assertEquals(ApiStatus.PARTIALLY_REFUNDED, transactionCaptor.getValue().status());
        verify(refunds, times(2)).save(any(ApiRefund.class));
    }

    @Test
    void fullRefundMovesApiToRefunded() {
        service.create(api.reference(), request("100.00"), "refund-key", "client-1");

        var transactionCaptor = org.mockito.ArgumentCaptor.forClass(ApiTransaction.class);
        verify(transactions).save(transactionCaptor.capture());
        assertEquals(ApiStatus.REFUNDED, transactionCaptor.getValue().status());
    }

    @Test
    void refundAboveRemainingBalanceIsRejectedWithoutSaving() {
        when(refunds.sumRefunded(api.reference())).thenReturn(new BigDecimal("80.00"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(api.reference(), request("20.01"), "refund-key", "client-1"));

        assertEquals("REFUND_EXCEEDS_REMAINING", exception.getCode());
        assertEquals(422, exception.getHttpStatus());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void refundWithMoreThanTwoDecimalPlacesIsRejected() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(api.reference(), request("1.001"), "refund-key", "client-1"));

        assertEquals("INVALID_AMOUNT_PRECISION", exception.getCode());
        verify(refunds, never()).save(any());
        verify(transactions, never()).findByReferenceAndClientForUpdate(any(), any());
    }

    @Test
    void pendingApiCannotBeRefunded() {
        ApiTransaction pendingApi = ApiTransaction.create("api-key", "client-1",
                ApiProvider.MVOLA_MOCK, "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.PENDING, null, null);
        when(transactions.findByReferenceAndClientForUpdate(pendingApi.reference(), "client-1"))
                .thenReturn(Optional.of(pendingApi));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(pendingApi.reference(), request("10.00"), "refund-key", "client-1"));

        assertEquals("REFUND_NOT_ALLOWED", exception.getCode());
        verify(refunds, never()).save(any());
    }

    @Test
    void repeatedIdenticalRefundReturnsPriorRefundWithoutSavingAgain() {
        ApiRefund existing = ApiRefund.pending("refund-key", "client-1", api.reference(),
            new BigDecimal("25.00"), "duplicate").transitionTo(RefundStatus.REFUNDED);
        when(refunds.findByClientAndKey("client-1", "refund-key")).thenReturn(Optional.of(existing));

        var response = service.create(api.reference(), request("25.00", "duplicate"), "refund-key", "client-1");

        assertEquals(existing.reference(), response.refundReference());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void conflictingRefundKeyForDifferentRequestIsRejected() {
        ApiRefund existing = ApiRefund.pending("refund-key", "client-1", api.reference(),
                new BigDecimal("25.00"), "ancien motif").transitionTo(RefundStatus.REFUNDED);
        when(refunds.findByClientAndKey("client-1", "refund-key")).thenReturn(Optional.of(existing));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(api.reference(), request("25.00", "nouveau motif"), "refund-key", "client-1"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void missingRefundIdempotencyKeyIsRejected() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(api.reference(), request("10.00"), " ", "client-1"));

        assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
        assertEquals(400, exception.getHttpStatus());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void repeatedRequestFindsRefundCreatedWhileWaitingForApiLock() {
        ApiRefund existing = ApiRefund.pending("refund-key", "client-1", api.reference(),
            new BigDecimal("25.00"), "Remboursement test").transitionTo(RefundStatus.REFUNDED);
        AtomicInteger lookupCount = new AtomicInteger();
        when(refunds.findByClientAndKey("client-1", "refund-key"))
            .thenAnswer(invocation -> lookupCount.getAndIncrement() == 0
                ? Optional.empty()
                : Optional.of(existing));

        var response = service.create(api.reference(), request("25.00"), "refund-key", "client-1");

        assertEquals(existing.reference(), response.refundReference());
        verify(transactions).findByReferenceAndClientForUpdate(api.reference(), "client-1");
        verify(refunds, never()).sumRefunded(any());
        verify(refunds, never()).save(any());
        verify(transactions, never()).save(any());
    }

    @Test
    void missingApiReturnsNotFound() {
        when(transactions.findByReferenceAndClientForUpdate("PAY-missing", "client-1"))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create("PAY-missing", request("10.00"), "refund-key", "client-1"));

        assertEquals("TRANSACTION_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getHttpStatus());
        verify(refunds, never()).save(any());
    }

    @Test
    void refundListRequiresApiOwnership() {
        when(transactions.findByReferenceAndClient("PAY-missing", "client-1")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
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
