package com.soapay.api.infrastructure.persistence;

import com.soapay.api.application.port.out.ApiRefundStore;
import com.soapay.api.application.port.out.ApiTransactionStore;
import com.soapay.api.domain.enums.ApiProvider;
import com.soapay.api.domain.enums.ApiStatus;
import com.soapay.api.domain.enums.RefundStatus;
import com.soapay.api.domain.model.ApiRefund;
import com.soapay.api.domain.model.ApiTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@SuppressWarnings("deprecation")
class PostgresPersistenceIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private ApiTransactionStore transactions;

    @Autowired
    private ApiRefundStore refunds;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migrationsAndTypedRefundStatusesWorkOnPostgres() {
        ApiTransaction api = ApiTransaction.create("pg-payment-" + System.nanoTime(), "pg-client",
                ApiProvider.MVOLA_MOCK, "0340000000", new BigDecimal("100.00"), "MGA", null)
                .transitionTo(ApiStatus.SUCCESS, null, null);
        transactions.saveAndFlush(api);

        ApiRefund pending = ApiRefund.pending("pg-refund-" + System.nanoTime(), "pg-client", api.reference(),
                new BigDecimal("25.00"), "PostgreSQL integration");
        ApiRefund savedPending = refunds.save(pending);
        assertEquals(RefundStatus.PENDING, savedPending.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(refunds.sumRefunded(api.reference())));

        ApiRefund completed = refunds.save(pending.transitionTo(RefundStatus.REFUNDED));
        assertEquals(RefundStatus.REFUNDED, completed.status());
        assertNotNull(completed.processedAt());
        assertEquals(0, new BigDecimal("25.00").compareTo(refunds.sumRefunded(api.reference())));
        assertEquals(1, jdbc.queryForObject(
            "select count(*) from api_refunds where transaction_reference = ?", Integer.class, api.reference()));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update api_refunds set status = ? where refund_reference = ?", "UNKNOWN", pending.reference()));
    }
}
