package com.example.payment.application.usecase;

import com.example.payment.application.dto.request.CreatePaymentRequest;
import com.example.payment.application.port.out.PaymentProviderPort;
import com.example.payment.application.port.out.PaymentTransactionStore;
import com.example.payment.domain.enums.PaymentProvider;
import com.example.payment.domain.enums.PaymentStatus;
import com.example.payment.domain.enums.SimulationResult;
import com.example.payment.domain.exception.PaymentException;
import com.example.payment.domain.model.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {
    private PaymentTransactionStore store;
    private PaymentProviderRegistry providers;
    private PaymentProviderPort provider;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        store = mock(PaymentTransactionStore.class);
        providers = mock(PaymentProviderRegistry.class);
        provider = mock(PaymentProviderPort.class);
        service = new PaymentService(store, providers);

        when(store.findByClientAndKey(any(), any())).thenReturn(Optional.empty());
        when(providers.get(PaymentProvider.MVOLA_MOCK)).thenReturn(provider);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.isPhoneNumberValid("0340000000")).thenReturn(true);
        when(provider.simulatePayment(any(), any())).thenReturn(SimulationResult.SUCCESS);
        when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(SimulationResult.class)
    void createMapsProviderResultToTransactionStatus(SimulationResult result) {
        when(provider.simulatePayment(any(), any())).thenReturn(result);

        var response = service.create(request(new BigDecimal("100.00")), "key-1", "client-1");

        PaymentStatus expected = switch (result) {
            case SUCCESS -> PaymentStatus.SUCCESS;
            case PENDING -> PaymentStatus.PENDING;
            case FAILED, TIMEOUT -> PaymentStatus.FAILED;
        };
        assertEquals(expected, response.status());
        assertTrue(response.simulated());
        verify(store).save(any(PaymentTransaction.class));
    }

    @Test
    void sameIdempotencyKeyAndRequestReturnsExistingPaymentWithoutCallingProvider() {
        PaymentTransaction existing = PaymentTransaction.create("key-1", "client-1", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.0"), "MGA", "Test");
        when(store.findByClientAndKey("client-1", "key-1")).thenReturn(Optional.of(existing));

        var response = service.create(request(new BigDecimal("100.00")), "key-1", "client-1");

        assertEquals(existing.reference(), response.transactionReference());
        verify(provider, never()).simulatePayment(any(), any());
        verify(store, never()).save(any());
    }

    @Test
    void reusingIdempotencyKeyForDifferentRequestIsRejected() {
        PaymentTransaction existing = PaymentTransaction.create("key-1", "client-1", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", "Test");
        when(store.findByClientAndKey("client-1", "key-1")).thenReturn(Optional.of(existing));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.create(request(new BigDecimal("101.00")), "key-1", "client-1"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        verify(provider, never()).simulatePayment(any(), any());
    }

    @Test
    void invalidIdempotencyKeyIsRejectedBeforeStoreLookup() {
        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.create(request(new BigDecimal("100.00")), " ", "client-1"));

        assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
        verify(store, never()).findByClientAndKey(any(), any());
    }

    @Test
    void unavailableProviderIsRejectedWithoutSaving() {
        when(provider.isAvailable()).thenReturn(false);

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.create(request(new BigDecimal("100.00")), "key-1", "client-1"));

        assertEquals("PROVIDER_UNAVAILABLE", exception.getCode());
        assertEquals(503, exception.getHttpStatus());
        verify(provider, never()).simulatePayment(any(), any());
        verify(store, never()).save(any());
    }

    @Test
    void getRejectsPaymentNotOwnedByClient() {
        when(store.findByReferenceAndClient(eq("PAY-unknown"), eq("client-1"))).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.get("PAY-unknown", "client-1"));

        assertEquals("TRANSACTION_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getHttpStatus());
    }

    @Test
    void confirmOnlyAllowsPendingPayment() {
        PaymentTransaction transaction = PaymentTransaction.create("key-1", "client-1", PaymentProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(PaymentStatus.SUCCESS, null, null);
        when(store.findByReferenceForUpdate(transaction.reference())).thenReturn(Optional.of(transaction));

        PaymentException exception = assertThrows(PaymentException.class,
                () -> service.confirm(transaction.reference()));

        assertEquals("INVALID_STATE_TRANSITION", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        verify(store, never()).save(any());
    }

    private static CreatePaymentRequest request(BigDecimal amount) {
        return new CreatePaymentRequest(PaymentProvider.MVOLA_MOCK, "0340000000", amount, "MGA", "Test");
    }
}
