package com.arijit.authservice.security;

import com.arijit.authservice.entity.User;
import com.arijit.authservice.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * An alternative to the Bearer/JWT flow for programmatic or
 * service-to-service callers that would rather hold one long-lived
 * credential than manage a refresh cycle. Presented via the
 * {@code X-API-Key} header; if present and valid, authenticates the
 * request as the key's owning user (with that user's actual roles, via
 * CustomUserDetailsService - a revoked/disabled user's keys stop working
 * automatically).
 * <p>
 * Not a {@code @Component} for the same reason JwtAuthFilter isn't - see
 * its javadoc.
 */
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER_NAME);

        if (rawKey != null && !rawKey.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<User> owner = apiKeyService.validateAndTouch(rawKey);
            if (owner.isPresent()) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(owner.get().getEmail());
                if (userDetails.isEnabled()) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            // Any other outcome (unknown/revoked key, disabled owner) is
            // deliberately silent here - same as JwtAuthFilter, this just
            // leaves the request unauthenticated and lets
            // authorizeHttpRequests decide whether that's allowed.
        }

        filterChain.doFilter(request, response);
    }
}
