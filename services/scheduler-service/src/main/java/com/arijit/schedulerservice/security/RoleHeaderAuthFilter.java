package com.arijit.schedulerservice.security;

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
 * scheduler-service never sees a client's JWT directly - the Gateway
 * already verified it and forwards who the caller is via
 * {@code X-User-Email}/{@code X-User-Roles} headers, same convention as
 * monitoring-service/feature-flag-service/notification-service.
 * This filter trusts those headers and builds the same kind of
 * Authentication a JWT filter would.
 * <p>
 * Added alongside the pre-existing InternalTokenAuthFilter (not replacing
 * it) - other platform services still call /jobs/** with the shared
 * internal token, same as before. Without this filter, a human/admin
 * caller arriving through the Gateway's new SCHEDULER route had no way to
 * authenticate at all: the Gateway forwards X-User-Email/X-User-Roles, not
 * the internal token, so every proxied request would have 401'd.
 * <p>
 * <b>Trust boundary:</b> this only works because scheduler-service's HTTP
 * port is not reachable from outside the platform's internal network -
 * every external (human/admin-console) request goes through the Gateway.
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
