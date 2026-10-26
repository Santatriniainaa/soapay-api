package com.soapay.api.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

final class KeycloakJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final String clientId;

    KeycloakJwtGrantedAuthoritiesConverter(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("Keycloak client ID must be configured");
        }
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
        addRoles(authorities, jwt.getClaim("roles"));

        Object realmAccessClaim = jwt.getClaim("realm_access");
        if (realmAccessClaim instanceof Map<?, ?> realmAccess) {
            addRoles(authorities, realmAccess.get("roles"));
        }

        Object resourceAccessClaim = jwt.getClaim("resource_access");
        if (resourceAccessClaim instanceof Map<?, ?> resourceAccess) {
            Object clientAccessClaim = resourceAccess.get(clientId);
            if (clientAccessClaim instanceof Map<?, ?> clientAccess) {
                addRoles(authorities, clientAccess.get("roles"));
            }
        }

        return authorities;
    }

    private static void addRoles(LinkedHashSet<GrantedAuthority> authorities, Object rolesClaim) {
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return;
        }
        for (Object roleClaim : roles) {
            if (roleClaim instanceof String role && !role.isBlank()) {
                String normalizedRole = role.startsWith("ROLE_") ? role.substring(5) : role;
                authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
            }
        }
    }
}