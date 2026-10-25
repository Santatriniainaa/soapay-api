package com.soapay.api.domain.model;

import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiTransactionTest {
    @Test
    void creationRejectsNonPositiveOrOverPrecisionAmounts() {
    assertEquals("INVALID_AMOUNT", assertThrows(ApiException.class,
        () -> ApiTransaction.create("key", "client", ApiProvider.MVOLA_MOCK,
            "0340000000", BigDecimal.ZERO, "MGA", null)).getCode());
    assertEquals("INVALID_AMOUNT_PRECISION", assertThrows(ApiException.class,
        () -> ApiTransaction.create("key", "client", ApiProvider.MVOLA_MOCK,
            "0340000000", new BigDecimal("1.001"), "MGA", null)).getCode());
    }

    @Test
    void creationRejectsUnknownOrMalformedCurrency() {
    assertEquals("INVALID_CURRENCY", assertThrows(ApiException.class,
        () -> ApiTransaction.create("key", "client", ApiProvider.MVOLA_MOCK,
            "0340000000", new BigDecimal("1.00"), "mga", null)).getCode());
    assertEquals("INVALID_CURRENCY", assertThrows(ApiException.class,
        () -> ApiTransaction.create("key", "client", ApiProvider.MVOLA_MOCK,
            "0340000000", new BigDecimal("1.00"), "ZZZ", null)).getCode());
    }

    @Test
    void successfulApiCannotBeCancelled() {
        ApiTransaction transaction = ApiTransaction.create("key-1", "client", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.SUCCESS, null, null);

        assertThrows(ApiException.class, () -> transaction.transitionTo(ApiStatus.CANCELLED, null, null));
    }

    @Test
    void pendingApiCanBeConfirmed() {
        ApiTransaction transaction = ApiTransaction.create("key-1", "client", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.PENDING, null, null)
                .transitionTo(ApiStatus.SUCCESS, null, null);

        assertEquals(ApiStatus.SUCCESS, transaction.status());
        assertEquals(new BigDecimal("100.00"), transaction.amount());
    }

    @Test
    void successfulAndPartiallyRefundedApisRemainRefundable() {
        assertFalse(ApiStatus.SUCCESS.isTerminal());
        assertFalse(ApiStatus.PARTIALLY_REFUNDED.isTerminal());
        assertTrue(ApiStatus.REFUNDED.isTerminal());
        assertTrue(ApiStatus.FAILED.isTerminal());
    }

    @Test
    void failedApiCannotTransitionAgain() {
        ApiTransaction failed = ApiTransaction.create("key-1", "client", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.FAILED, "DECLINED", "Declined");

        ApiException exception = assertThrows(ApiException.class,
                () -> failed.transitionTo(ApiStatus.SUCCESS, null, null));

        assertEquals("INVALID_STATE_TRANSITION", exception.getCode());
        assertEquals(409, exception.getHttpStatus());
        assertEquals("DECLINED", failed.failureCode());
        assertNotNull(failed.completedAt());
    }

    @Test
    void pendingTransitionDoesNotCompleteApi() {
        ApiTransaction created = ApiTransaction.create("key-1", "client", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null);

        ApiTransaction pending = created.transitionTo(ApiStatus.PENDING, null, null);

        assertEquals(ApiStatus.PENDING, pending.status());
        assertEquals(created.createdAt(), pending.createdAt());
        assertTrue(pending.completedAt() == null);
        assertNotNull(pending.updatedAt());
    }

    @Test
    void successfulApiCanTransitionThroughRefundPendingToPartialRefund() {
        ApiTransaction transaction = ApiTransaction.create("key-1", "client", ApiProvider.MVOLA_MOCK,
                "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.SUCCESS, null, null)
                .transitionTo(ApiStatus.REFUND_PENDING, null, null)
                .transitionTo(ApiStatus.PARTIALLY_REFUNDED, null, null);

        assertEquals(ApiStatus.PARTIALLY_REFUNDED, transaction.status());
        assertThrows(ApiException.class,
                () -> transaction.transitionTo(ApiStatus.SUCCESS, null, null));
    }
}
