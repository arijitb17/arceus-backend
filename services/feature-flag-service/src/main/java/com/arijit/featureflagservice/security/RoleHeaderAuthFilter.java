package com.arijit.featureflagservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * feature-flag-service never sees a client's JWT directly - the Gateway
 * already verified it and forwards who the caller is via
 * {@code X-User-Email}/{@code X-User-Roles} headers, same convention as
 * audit-service and notification-service. This filter trusts those
 * headers and builds the same kind of Authentication a JWT filter would,
 * so {@code @PreAuthorize} on FlagController works exactly like it would
 * in a service that validates tokens itself.
 * <p>
 * <b>Trust boundary:</b> this only works because feature-flag-service's
 * HTTP port is not reachable from outside the platform's internal
 * network - every external (human/admin-console) request goes through
 * the Gateway. Service-to-service callers that don't have a user JWT at
 * all (e.g. a background job evaluating a flag) authenticate instead via
 * {@link InternalTokenAuthFilter}.
 */
public class RoleHeaderAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String email = request.getHeader(HEADER_USER_EMAIL);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);

        if (email != null && !email.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            List<GrantedAuthority> authorities = (rolesHeader == null || rolesHeader.isBlank())
                    ? List.of()
                    : Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList();

            var authToken = new UsernamePasswordAuthenticationToken(email, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
