package com.example.payment.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health", "/h2-console/**").permitAll()
                        .requestMatchers("/api/v1/**").hasAnyRole("CLIENT", "ADMIN", "TEST_OPERATOR")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults()).build();
    }

    @Bean
        PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder,
            @Value("${demo.security.client.username:demo-client}") String clientName,
            @Value("${demo.security.client.password:change-me-client}") String clientPassword,
            @Value("${demo.security.admin.username:demo-admin}") String adminName,
            @Value("${demo.security.admin.password:change-me-admin}") String adminPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername(clientName).password(encoder.encode(clientPassword)).roles("CLIENT").build(),
                User.withUsername(adminName).password(encoder.encode(adminPassword)).roles("ADMIN", "TEST_OPERATOR").build());
    }
}
