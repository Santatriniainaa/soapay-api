package com.soapay.api.application.usecase;

import com.soapay.api.application.dto.request.CreateApiRequest;
import com.soapay.api.application.port.out.ApiProviderPort;
import com.soapay.api.application.port.out.ApiTransactionStore;
import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.enums.SimulationResult;
import com.soapay.api.domain.exception.ApiException;
import com.soapay.api.domain.model.ApiTransaction;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiServiceTest {
    private ApiTransactionStore store;
    private ApiProviderRegistry providers;
    private ApiProviderPort provider;
    private ApiService service;

    @BeforeEach
    void setUp() {
        store = mock(ApiTransactionStore.class);
        providers = mock(ApiProviderRegistry.class);
        provider = mock(ApiProviderPort.class);
        ApiCreationService creationService = new ApiCreationService(
            store, providers, new ApiCreationAttemptService(store));
        service = new ApiService(store, creationService);

        when(store.findByClientAndKey(any(), any())).thenReturn(Optional.empty());
        when(providers.get(ApiProvider.MVOLA_MOCK)).thenReturn(provider);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.isPhoneNumberValid("0340000000")).thenReturn(true);
        when(provider.simulateApi(any(), any())).thenReturn(SimulationResult.SUCCESS);
        when(store.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(store.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(SimulationResult.class)
    void createMapsProviderResultToTransactionStatus(SimulationResult result) {
        when(provider.simulateApi(any(), any())).thenReturn(result);

        var response = service.create(request(new BigDecimal("100.00")), "key-1", "client-1");

        ApiStatus expected = switch (result) {
            case SUCCESS -> ApiStatus.SUCCESS;
            case PENDING -> ApiStatus.PENDING;
            case FAILED, TIMEOUT -> ApiStatus.FAILED;
        };
        assertEquals(expected, response.status());
        assertTrue(response.simulated());
        verify(store).saveAndFlush(any(ApiTransaction.class));
        verify(store).save(any(ApiTransaction.class));
    }

    @Test
    void sameIdempotencyKeyAndRequestReturnsExistingApiWithoutCallingProvider() {
        ApiTransaction existing = ApiTransaction.create("key-1", "client-1", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.0"), "MGA", "Test");
        when(store.findByClientAndKey("client-1", "key-1")).thenReturn(Optional.of(existing));

        var response = service.create(request(new BigDecimal("100.00")), "key-1", "client-1");

        assertEquals(existing.reference(), response.transactionReference());
        verify(provider, never()).simulateApi(any(), any());
        verify(store, never()).save(any());
    }

    @Test
    void reusingIdempotencyKeyForDifferentRequestIsRejected() {
        ApiTransaction existing = ApiTransaction.create("key-1", "client-1", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", "Test");
        when(store.findByClientAndKey("client-1", "key-1")).thenReturn(Optional.of(existing));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(request(new BigDecimal("101.00")), "key-1", "client-1"));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        verify(provider, never()).simulateApi(any(), any());
    }

    @Test
    void nullRequestIsRejectedBeforeAnyProviderCall() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(null, "key-1", "client-1"));

        assertEquals("INVALID_REQUEST", exception.getCode());
        assertEquals(400, exception.getHttpStatus());
        verify(store, never()).findByClientAndKey(any(), any());
        verify(provider, never()).simulateApi(any(), any());
    }

    @Test
    void invalidPhoneNumberIsRejectedBeforeSimulation() {
        when(provider.isPhoneNumberValid("0340000000")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(request(new BigDecimal("100.00")), "key-1", "client-1"));

        assertEquals("INVALID_PHONE_NUMBER", exception.getCode());
        assertEquals(422, exception.getHttpStatus());
        verify(provider, never()).simulateApi(any(), any());
    }

    @ParameterizedTest
    @MethodSource("invalidCurrencyRequests")
    void invalidCurrencyIsRejected(CreateApiRequest request, String expectedCode) {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(request, "key-1", "client-1"));

        assertEquals(expectedCode, exception.getCode());
        assertEquals(400, exception.getHttpStatus());
        verify(provider, never()).simulateApi(any(), any());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidCurrencyRequests() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        new CreateApiRequest(ApiProvider.MVOLA_MOCK, "0340000000", new BigDecimal("100.00"), "ZZZ", "Test"),
                        "INVALID_CURRENCY"),
                org.junit.jupiter.params.provider.Arguments.of(
                        new CreateApiRequest(ApiProvider.MVOLA_MOCK, "0340000000", new BigDecimal("100.00"), "usd", "Test"),
                        "INVALID_CURRENCY")
        );
    }

    @Test
    void concurrentDuplicateCreationReturnsPaymentPersistedByWinningRequest() {
        ApiTransaction existing = ApiTransaction.create("key-1", "client-1", ApiProvider.MVOLA_MOCK,
            "0340000000", new BigDecimal("100.00"), "MGA", "Test");
        ApiCreationAttemptService attempt = mock(ApiCreationAttemptService.class);
        ApiCreationService concurrentCreation = new ApiCreationService(store, providers, attempt);
        ApiService concurrentService = new ApiService(store, concurrentCreation);
        when(store.findByClientAndKey("client-1", "key-1"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existing));
        when(providers.get(ApiProvider.MVOLA_MOCK)).thenReturn(provider);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.isPhoneNumberValid("0340000000")).thenReturn(true);
        when(attempt.execute(any(), eq("key-1"), eq("client-1"), eq(provider)))
            .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        var response = concurrentService.create(request(new BigDecimal("100.00")), "key-1", "client-1");

        assertEquals(existing.reference(), response.transactionReference());
        verify(attempt).execute(any(), eq("key-1"), eq("client-1"), eq(provider));
    }

    @Test
    void invalidIdempotencyKeyIsRejectedBeforeStoreLookup() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(request(new BigDecimal("100.00")), " ", "client-1"));

        assertEquals("IDEMPOTENCY_KEY_REQUIRED", exception.getCode());
        verify(store, never()).findByClientAndKey(any(), any());
    }

    @Test
    void unavailableProviderIsRejectedWithoutSaving() {
        when(provider.isAvailable()).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(request(new BigDecimal("100.00")), "key-1", "client-1"));

        assertEquals("PROVIDER_UNAVAILABLE", exception.getCode());
        assertEquals(503, exception.getHttpStatus());
        verify(provider, never()).simulateApi(any(), any());
        verify(store, never()).save(any());
    }

    @Test
    void getRejectsApiNotOwnedByClient() {
        when(store.findByReferenceAndClient(eq("PAY-unknown"), eq("client-1"))).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.get("PAY-unknown", "client-1"));

        assertEquals("TRANSACTION_NOT_FOUND", exception.getCode());
        assertEquals(404, exception.getHttpStatus());
    }

    @Test
    void confirmOnlyAllowsPendingApi() {
        ApiTransaction transaction = ApiTransaction.create("key-1", "client-1", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.SUCCESS, null, null);
        when(store.findByReferenceForUpdate(transaction.reference())).thenReturn(Optional.of(transaction));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.confirm(transaction.reference()));

        assertEquals("INVALID_STATE_TRANSITION", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        verify(store, never()).save(any());
    }

    private static CreateApiRequest request(BigDecimal amount) {
        return new CreateApiRequest(ApiProvider.MVOLA_MOCK, "0340000000", amount, "MGA", "Test");
    }
}
