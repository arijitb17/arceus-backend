package com.arijit.schedulerservice.security;

import com.arijit.schedulerservice.config.SchedulerProperties;
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
 * scheduler-service is called directly by other platform services
 * (auth-service registering session-cleanup jobs, cache-service
 * registering cache-warmup jobs, ...) rather than by end users through
 * the Gateway - so unlike a Gateway-fronted service that trusts
 * X-User-Email/X-User-Roles headers set after JWT verification, this
 * trusts a single shared secret every internal caller presents via
 * {@code X-Internal-Token}, configured as {@code arceus.scheduler.internal.api-token}
 * on both sides.
 * <p>
 * <b>Trust boundary:</b> this only works because scheduler-service's HTTP
 * and gRPC ports are not reachable from outside the platform's internal
 * network. If this port is ever exposed directly to untrusted callers,
 * this shared secret is the entire security model - don't do that without
 * per-caller credentials instead of one platform-wide token.
 */
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenAuthFilter(SchedulerProperties properties) {
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
