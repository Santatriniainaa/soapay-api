package com.soapay.api.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityIntegrationTest {
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
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
