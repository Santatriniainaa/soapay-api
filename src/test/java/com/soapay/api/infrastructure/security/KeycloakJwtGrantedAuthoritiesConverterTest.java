package com.soapay.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KeycloakJwtGrantedAuthoritiesConverterTest {
    private final KeycloakJwtGrantedAuthoritiesConverter converter =
            new KeycloakJwtGrantedAuthoritiesConverter("soapay-api");

    @Test
    void convertsRealmAndConfiguredClientRoles() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("realm_access", Map.of("roles", List.of("CLIENT", "offline_access")))
                .claim("resource_access", Map.of(
                        "soapay-api", Map.of("roles", List.of("ADMIN")),
                        "another-client", Map.of("roles", List.of("TEST_OPERATOR"))))
                .build();

        assertEquals(Set.of("ROLE_CLIENT", "ROLE_offline_access", "ROLE_ADMIN"), authorities(converter.convert(jwt)));
    }

    @Test
    void preservesDirectRolesClaimCompatibilityAndDeduplicatesAuthorities() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("roles", List.of("CLIENT", "ROLE_CLIENT"))
                .build();

        assertEquals(Set.of("ROLE_CLIENT"), authorities(converter.convert(jwt)));
    }

    private static Set<String> authorities(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(authority -> authority.getAuthority()).collect(Collectors.toSet());
    }
}