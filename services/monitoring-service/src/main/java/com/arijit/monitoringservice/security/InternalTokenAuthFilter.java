package com.arijit.monitoringservice.security;

import com.arijit.monitoringservice.config.MonitoringProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Second authentication path alongside {@link RoleHeaderAuthFilter}: other
 * platform services reading platform health/metrics don't have a user JWT
 * at all, so they present a shared secret via {@code X-Internal-Token}
 * instead, configured as {@code arceus.monitoring.internal.api-token} on
 * both sides. Granted {@code ROLE_INTERNAL}, treated by AlertController's
 * {@code @PreAuthorize} rules as equivalent to an admin for read endpoints
 * but NOT for alert-rule mutation - an internal caller can read platform
 * state but should not be able to originate an arbitrary alert-rule change.
 * <p>
 * <b>Trust boundary:</b> same as feature-flag-service's InternalTokenAuthFilter
 * - this only works because this service's ports aren't reachable from
 * outside the platform's internal network.
 */
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenAuthFilter(MonitoringProperties properties) {
        this.expectedToken = properties.internal().apiToken();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && !presented.isBlank() && presented.equals(expectedToken)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authToken = new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }
}
