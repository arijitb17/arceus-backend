package com.arijit.authservice.security;

import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Slf4j
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
                    log.debug("Authenticated via API key: keyId={} prefix={} project={}",
                            apiKey.getId(), apiKey.getKeyPrefix(), apiKey.getProjectId());

                    if (apiKey.getProjectId() != null) {
                        request.setAttribute(ATTR_ORGANIZATION_ID, apiKey.getOrganizationId().toString());
                        request.setAttribute(ATTR_PROJECT_ID, apiKey.getProjectId().toString());
                        request.setAttribute(ATTR_ENVIRONMENT, apiKey.getEnvironment());
                        request.setAttribute(ATTR_KEY_TYPE, apiKey.getKeyType().name());
                        if (apiKey.getScopes() != null) {
                            request.setAttribute(ATTR_SCOPES, apiKey.getScopes());
                        }
                    }
                } else {
                    log.warn("API key rejected: owner account disabled, keyId={}", apiKey.getId());
                }
            } else {
                log.debug("API key rejected: unknown or revoked key for {} {}", request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }
}
