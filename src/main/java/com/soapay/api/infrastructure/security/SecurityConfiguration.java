package com.soapay.api.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
        @Profile("!prod")
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**", "/h2-console/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/apis/*/confirm").hasAnyRole("ADMIN", "TEST_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/apis", "/api/v1/apis/*/cancel",
                                "/api/v1/apis/*/refunds").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/apis", "/api/v1/apis/*",
                                "/api/v1/apis/*/refunds", "/api/v1/refunds/*").hasRole("CLIENT")
                        .requestMatchers("/api/v1/providers", "/api/v1/providers/**")
                                .hasAnyRole("CLIENT", "ADMIN", "TEST_OPERATOR")
                        .requestMatchers("/api/v1/**").hasAnyRole("CLIENT", "ADMIN", "TEST_OPERATOR")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
    @Profile("prod")
    SecurityFilterChain productionSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/apis/*/confirm").hasAnyRole("ADMIN", "TEST_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/apis", "/api/v1/apis/*/cancel",
                                "/api/v1/apis/*/refunds").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/apis", "/api/v1/apis/*",
                                "/api/v1/apis/*/refunds", "/api/v1/refunds/*").hasRole("CLIENT")
                        .requestMatchers("/api/v1/providers", "/api/v1/providers/**")
                                .hasAnyRole("CLIENT", "ADMIN", "TEST_OPERATOR")
                        .requestMatchers("/api/v1/**").hasAnyRole("CLIENT", "ADMIN", "TEST_OPERATOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

        @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

    @Bean
        @Profile("!prod")
        UserDetailsService userDetailsService(PasswordEncoder encoder,
                        @Value("${demo.security.client.username}") String clientName,
                        @Value("${demo.security.client.password}") String clientPassword,
                        @Value("${demo.security.admin.username}") String adminName,
                        @Value("${demo.security.admin.password}") String adminPassword) {
                if (clientName.isBlank() || adminName.isBlank() || clientPassword.isBlank() || adminPassword.isBlank()) {
                        throw new IllegalStateException("Les identifiants de démonstration doivent être configurés");
                }
        return new InMemoryUserDetailsManager(
                User.withUsername(clientName).password(encoder.encode(clientPassword)).roles("CLIENT").build(),
                User.withUsername(adminName).password(encoder.encode(adminPassword)).roles("ADMIN", "TEST_OPERATOR").build());
    }
}
