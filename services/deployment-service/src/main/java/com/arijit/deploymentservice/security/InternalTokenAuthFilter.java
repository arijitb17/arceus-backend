package com.arijit.deploymentservice.security;

import com.arijit.deploymentservice.config.DeploymentProperties;
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
 * platform services (or an internal ops tool) reading/creating deployments
 * don't have a user JWT at all, so they present a shared secret via
 * {@code X-Internal-Token} instead, configured as
 * {@code arceus.deployment.internal.api-token} on both sides. Granted
 * {@code ROLE_INTERNAL} - sufficient for read endpoints and POST
 * /deployments, but NOT for the ROLE_ADMIN-gated approve/deploy/rollback/
 * cancel actions, same posture as monitoring-service's InternalTokenAuthFilter
 * (an internal caller can read/propose but should not originate an
 * arbitrary production deployment/rollback on its own).
 * <p>
 * <b>Trust boundary:</b> this only works because this service's ports
 * aren't reachable from outside the platform's internal network.
 */
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenAuthFilter(DeploymentProperties properties) {
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
