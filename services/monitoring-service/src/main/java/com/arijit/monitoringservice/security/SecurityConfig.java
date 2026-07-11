package com.arijit.monitoringservice.security;

import com.arijit.monitoringservice.config.MonitoringProperties;
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
 * Every read endpoint (GET /health, /services, /metrics, /alerts,
 * /dashboard, /stats) is available to any authenticated caller - "Read-only
 * metrics for most users" per the spec. Alert rule mutation
 * (POST/PUT/DELETE /alerts/**) additionally requires ROLE_ADMIN, enforced
 * via {@code @PreAuthorize} on AlertController - "Admin-only alert
 * management". Authentication itself is established by either
 * {@link RoleHeaderAuthFilter} (human/admin callers arriving via the
 * Gateway) or {@link InternalTokenAuthFilter} (other platform services).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final MonitoringProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new RoleHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new InternalTokenAuthFilter(properties), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
