package com.soapay.api.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    JwtAudienceValidator(String audience) {
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT audience must be configured");
        }
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience().contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "JWT audience does not contain the configured API audience",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}