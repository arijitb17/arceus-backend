package com.arijit.featureflagservice.security;

import com.arijit.featureflagservice.config.FeatureFlagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * RBAC enforcement lives on individual controller methods via
 * {@code @PreAuthorize} (see FlagController) - this class only wires up
 * *how* an Authentication ends up in the SecurityContext at all: either
 * {@link RoleHeaderAuthFilter} (human/admin callers arriving via the
 * Gateway, carrying whatever roles their JWT had) or
 * {@link InternalTokenAuthFilter} (other platform services, carrying
 * ROLE_INTERNAL). Only {@code /health}, OpenAPI docs, and the internal
 * scheduler-callback endpoints under {@code /internal/flags/**} (which
 * verify their own query-parameter token inside the controller - see
 * FlagScheduleCallbackController - since scheduler-service's
 * HTTP_CALLBACK executor can't attach custom headers) are open.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final FeatureFlagProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // scheduler-service's HTTP_CALLBACK executor can't attach custom
                        // headers per job (see HttpCallbackJobExecutor), so the shared
                        // token here travels as a query parameter instead of
                        // X-Internal-Token - FlagScheduleCallbackController verifies it
                        // itself rather than relying on InternalTokenAuthFilter.
                        .requestMatchers("/internal/flags/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new RoleHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new InternalTokenAuthFilter(properties), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
