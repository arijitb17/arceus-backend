package com.arijit.deploymentservice.security;

import com.arijit.deploymentservice.config.DeploymentProperties;
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
 * {@code @PreAuthorize} (see DeploymentController): creating and reading
 * deployments is available to any authenticated caller, while approve,
 * deploy, rollback, and cancel - every action that actually changes what's
 * running - require ROLE_ADMIN, same "read is open, mutation is
 * admin-only" split monitoring-service uses for alert rules. This class
 * only wires up *how* an Authentication ends up in the SecurityContext at
 * all: either {@link RoleHeaderAuthFilter} (human/operator callers arriving
 * via the Gateway) or {@link InternalTokenAuthFilter} (other platform
 * services). The scheduler-service scheduled-deploy callback under
 * {@code /internal/deployments/**} verifies its own query-parameter token
 * inside the controller instead (see DeploymentCallbackController) - same
 * reasoning as feature-flag-service's FlagScheduleCallbackController,
 * since scheduler-service's HTTP_CALLBACK executor can't attach a custom
 * header per job.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DeploymentProperties properties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/internal/deployments/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new RoleHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new InternalTokenAuthFilter(properties), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
