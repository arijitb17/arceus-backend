package com.arijit.featureflagservice.security;

import com.arijit.featureflagservice.config.FeatureFlagProperties;
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
 * platform services evaluating a flag (or scheduler-service's
 * HTTP_CALLBACK jobs flipping a flag's status at its scheduled
 * activate/deactivate time) don't have a user JWT at all, so they present
 * a shared secret via {@code X-Internal-Token} instead, configured as
 * {@code arceus.feature-flag.internal.api-token} on both sides. Granted
 * {@code ROLE_INTERNAL}, which FlagController's {@code @PreAuthorize}
 * rules treat as equivalent to an admin for read/evaluate endpoints but
 * NOT for the human-facing create/update/delete endpoints - an internal
 * caller can read and can be called back by the scheduler, but should not
 * be able to originate an arbitrary flag mutation.
 * <p>
 * <b>Trust boundary:</b> same as scheduler-service's InternalTokenAuthFilter
 * - this only works because this service's ports aren't reachable from
 * outside the platform's internal network.
 */
public class InternalTokenAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenAuthFilter(FeatureFlagProperties properties) {
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
