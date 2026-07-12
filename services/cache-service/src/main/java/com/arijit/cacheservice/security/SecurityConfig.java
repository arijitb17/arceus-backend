package com.arijit.cacheservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * cache-service previously had no Spring Security dependency at all - any
 * caller that could reach its HTTP port could read/write/evict any cache
 * entry in any namespace. Now that the Gateway proxies human/admin traffic
 * here (see gateway-service's CACHE route), that's no longer acceptable:
 * every {@code /cache/**} endpoint requires the {@code X-User-Email}/
 * {@code X-User-Roles} headers the Gateway sets after JWT verification -
 * see RoleHeaderAuthFilter. {@code /internal/cache/**} (the scheduled-sweep
 * callback) stays open at this layer since it does its own shared-secret
 * token check inside SweepCallbackController - scheduler-service's
 * HTTP_CALLBACK executor can't attach a custom auth header per job, same
 * reasoning as every other internal callback controller on the platform.
 * <p>
 * Other backend services still reach cache-service over gRPC, which
 * remains unauthenticated by design (same trust model as
 * auth/notification/audit/ratelimit's gRPC surface - see those services'
 * own GrpcClientConfig Javadoc) - this only locks down the REST surface
 * that's now reachable from outside the platform's internal network.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Checks its own ?token= - see SweepCallbackController.
                        .requestMatchers("/internal/cache/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new RoleHeaderAuthFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
