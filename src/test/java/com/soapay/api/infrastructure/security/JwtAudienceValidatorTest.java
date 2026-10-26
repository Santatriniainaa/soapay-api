package com.soapay.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAudienceValidatorTest {
    private final JwtAudienceValidator validator = new JwtAudienceValidator("soapay-api");

    @Test
    void acceptsTokenContainingConfiguredAudience() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .audience(List.of("account", "soapay-api"))
                .build();

        assertFalse(validator.validate(jwt).hasErrors());
    }

    @Test
    void rejectsTokenWithoutConfiguredAudience() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .audience(List.of("account"))
                .build();

        assertTrue(validator.validate(jwt).hasErrors());
    }
}