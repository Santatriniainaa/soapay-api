package com.soapay.api.domain.model;

import com.soapay.api.domain.enums.RefundStatus;
import com.soapay.api.domain.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundStatusTest {
    @Test
    void refundMovesFromPendingToSuccessWithProcessedTimestamp() {
        ApiRefund pending = ApiRefund.pending("refund-key", "client", "PAY-123",
                new BigDecimal("12.50"), null);

        assertEquals(RefundStatus.PENDING, pending.status());
        assertNull(pending.processedAt());

        ApiRefund refunded = pending.transitionTo(RefundStatus.REFUNDED);

        assertEquals(RefundStatus.REFUNDED, refunded.status());
        assertNotNull(refunded.processedAt());
    }

    @Test
    void completedRefundCannotTransitionAgain() {
        ApiRefund refunded = ApiRefund.pending("refund-key", "client", "PAY-123",
                new BigDecimal("12.50"), null).transitionTo(RefundStatus.REFUNDED);

        ApiException exception = assertThrows(ApiException.class,
                () -> refunded.transitionTo(RefundStatus.FAILED));

        assertEquals("INVALID_REFUND_TRANSITION", exception.getCode());
    }

    @Test
    void pendingRefundCanFailAndFailureIsTerminal() {
        ApiRefund pending = ApiRefund.pending("refund-key", "client", "PAY-123",
                new BigDecimal("12.50"), null);

        ApiRefund failed = pending.transitionTo(RefundStatus.FAILED);

        assertEquals(RefundStatus.FAILED, failed.status());
        assertNotNull(failed.processedAt());
        assertThrows(ApiException.class, () -> failed.transitionTo(RefundStatus.REFUNDED));
    }

    @Test
    void refundCreationRejectsMoreThanTwoFractionDigits() {
        ApiException exception = assertThrows(ApiException.class,
                () -> ApiRefund.pending("refund-key", "client", "PAY-123",
                        new BigDecimal("1.001"), null));

        assertEquals("INVALID_AMOUNT_PRECISION", exception.getCode());
    }
}
