package com.arijit.authservice.security;

import com.arijit.authservice.entity.ApiKey;
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
 * Recognizes both platform-level keys ({@code ak_}) and project-scoped
 * keys ({@code pk_}/{@code sk_}, architecture design §7) - either way the
 * request authenticates as the key's owning user. For a project-scoped
 * key, the resolved {@link ApiKey}'s project/environment/scopes are
 * stashed as request attributes ({@link #ATTR_PROJECT_ID} etc.) so a
 * controller (or, eventually, the Gateway's own tenant-resolution layer -
 * see the architecture design's §3, not yet built) can read them without
 * a second lookup. This filter only ever authenticates requests to
 * <i>auth-service's own</i> endpoints when called directly with an API
 * key (e.g. an admin's own CI key hitting {@code /api/auth/users/me}) -
 * propagating a resolved tenant context to every <i>other</i> service via
 * the Gateway is the still-open piece the design doc calls out.
 * <p>
 * Not a {@code @Component} for the same reason JwtAuthFilter isn't - see
 * its javadoc.
 */
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-API-Key";

    public static final String ATTR_ORGANIZATION_ID = "arceus.apiKey.organizationId";
    public static final String ATTR_PROJECT_ID = "arceus.apiKey.projectId";
    public static final String ATTR_ENVIRONMENT = "arceus.apiKey.environment";
    public static final String ATTR_KEY_TYPE = "arceus.apiKey.keyType";
    public static final String ATTR_SCOPES = "arceus.apiKey.scopes";

    private final ApiKeyService apiKeyService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER_NAME);

        if (rawKey != null && !rawKey.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<ApiKey> resolved = apiKeyService.validateAndTouch(rawKey);
            if (resolved.isPresent()) {
                ApiKey apiKey = resolved.get();
                UserDetails userDetails = userDetailsService.loadUserByUsername(apiKey.getUser().getEmail());
                if (userDetails.isEnabled()) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    if (apiKey.getProjectId() != null) {
                        request.setAttribute(ATTR_ORGANIZATION_ID, apiKey.getOrganizationId().toString());
                        request.setAttribute(ATTR_PROJECT_ID, apiKey.getProjectId().toString());
                        request.setAttribute(ATTR_ENVIRONMENT, apiKey.getEnvironment());
                        request.setAttribute(ATTR_KEY_TYPE, apiKey.getKeyType().name());
                        if (apiKey.getScopes() != null) {
                            request.setAttribute(ATTR_SCOPES, apiKey.getScopes());
                        }
                    }
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
