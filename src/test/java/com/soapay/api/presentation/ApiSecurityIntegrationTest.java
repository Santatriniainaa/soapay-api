package com.soapay.api.presentation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityIntegrationTest {
  @Autowired
  private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void apiApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/providers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedClientCanReadSimulatedProviders() throws Exception {
        mockMvc.perform(get("/api/v1/providers")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].simulated").value(true));
    }

    @Test
    void clientCannotConfirmApi() throws Exception {
        mockMvc.perform(post("/api/v1/apis/PAY-unknown/confirm")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachConfirmEndpointForAuthorizationCheck() throws Exception {
        mockMvc.perform(post("/api/v1/apis/PAY-unknown/confirm")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-admin", "test-admin-password")))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotCreateClientApi() throws Exception {
        mockMvc.perform(post("/api/v1/apis")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-admin", "test-admin-password"))
                        .header("Idempotency-Key", "admin-api-forbidden")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "MVOLA_MOCK",
                                  "phoneNumber": "0340000000",
                                  "amount": 1500.00,
                                  "currency": "MGA"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void validBasicAuthApiRequestDoesNotNeedBrowserCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/apis")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password"))
                        .header("Idempotency-Key", "integration-api-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "MVOLA_MOCK",
                                  "phoneNumber": "0340000000",
                                  "amount": 1500.00,
                                  "currency": "MGA",
                                  "description": "Integration test"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void invalidApiRequestReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/apis")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password"))
                        .header("Idempotency-Key", "integration-api-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "MVOLA_MOCK",
                                  "phoneNumber": "not-a-demo-number",
                                  "amount": 1500.00,
                                  "currency": "MGA"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listEndpointSupportsPagination() throws Exception {
        for (String key : java.util.List.of("page-1", "page-2", "page-3")) {
            mockMvc.perform(post("/api/v1/apis")
                            .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password"))
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "provider": "MVOLA_MOCK",
                                      "phoneNumber": "0340000000",
                                      "amount": 1500.00,
                                      "currency": "MGA",
                                      "description": "Pagination test"
                                    }
                                    """))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/v1/apis")
                        .param("page", "0")
                        .param("size", "1")
                        .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").exists())
                .andExpect(jsonPath("$.length()").value(1));
    }

                @Test
                void identicalRefundRetryReturnsSameReferenceAndPersistsOneRow() throws Exception {
              String apiReference = createSuccessfulApi("refund-payment-" + UUID.randomUUID());
              String idempotencyKey = "refund-retry-" + UUID.randomUUID();
              String request = """
                {
                  "amount": 250.00,
                  "reason": "Idempotency retry integration test"
                }
                """;

              MvcResult first = createRefund(apiReference, idempotencyKey, request);
              MvcResult retry = createRefund(apiReference, idempotencyKey, request);

              assertEquals(
                JsonPath.read(first.getResponse().getContentAsString(), "$.refundReference").toString(),
                JsonPath.read(retry.getResponse().getContentAsString(), "$.refundReference").toString());
              assertEquals(1, countRefunds("test-client", idempotencyKey));
                }

                @Test
                void refundKeyReusedWithDifferentContentReturnsConflictAndPersistsOneRow() throws Exception {
              String apiReference = createSuccessfulApi("refund-conflict-payment-" + UUID.randomUUID());
              String idempotencyKey = "refund-conflict-" + UUID.randomUUID();

              createRefund(apiReference, idempotencyKey, """
                {
                  "amount": 250.00,
                  "reason": "Original refund request"
                }
                """);

              mockMvc.perform(post("/api/v1/apis/{reference}/refunds", apiReference)
                  .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password"))
                  .header("Idempotency-Key", idempotencyKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("""
                    {
                      "amount": 250.00,
                      "reason": "Changed refund request"
                    }
                    """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

              assertEquals(1, countRefunds("test-client", idempotencyKey));
                }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

          private String createSuccessfulApi(String idempotencyKey) throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/apis")
                    .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password"))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "provider": "MVOLA_MOCK",
                          "phoneNumber": "0340000000",
                          "amount": 1000.00,
                          "currency": "MGA",
                          "description": "Refund idempotency integration test"
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();
            return JsonPath.read(result.getResponse().getContentAsString(), "$.transactionReference").toString();
          }

          private MvcResult createRefund(String apiReference, String idempotencyKey, String request) throws Exception {
            return mockMvc.perform(post("/api/v1/apis/{reference}/refunds", apiReference)
                    .with(SecurityMockMvcRequestPostProcessors.httpBasic("test-client", "test-client-password"))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request))
                .andExpect(status().isCreated())
                .andReturn();
          }

          private int countRefunds(String clientId, String idempotencyKey) {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM api_refunds WHERE client_id = ? AND idempotency_key = ?",
                Integer.class,
                clientId,
                idempotencyKey);
          }
}
