package com.arijit.gatewayservice.security;

import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import com.arijit.arceus.grpc.apikey.ValidateApiKeyRequest;
import com.arijit.arceus.grpc.apikey.ValidateApiKeyResponse;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates requests carrying an {@code X-API-Key} header - the
 * missing piece from the architecture design's §7 that makes a generated
 * SDK (§9) actually usable: until this filter existed, a project's PUBLIC/
 * SECRET key only worked against auth-service's own endpoints (see
 * auth-service's own ApiKeyAuthFilter), because nothing at the Gateway
 * knew how to authenticate one - every other route required a JWT.
 * <p>
 * Runs immediately before {@link JwtAuthFilter} (see FilterConfig's
 * ordering) and takes over entirely when the header is present: an
 * invalid API key fails the request outright rather than falling through
 * to the JWT check, since a caller presenting a key clearly isn't a
 * browser session and a fallback would just turn a clear 401 into a
 * confusing one. No key at all - the common case, browser traffic from
 * either Portal - passes straight through to JwtAuthFilter unchanged.
 * <p>
 * On success, stamps the same {@link JwtAuthFilter#USER_EMAIL_ATTRIBUTE}/
 * {@link JwtAuthFilter#USER_ROLES_ATTRIBUTE} attributes a JWT would (so
 * every downstream service's existing RoleHeaderAuthFilter needs zero
 * changes to accept either), plus the resolved project scope as separate
 * attributes HttpMessageMapper forwards as {@code X-Tenant-*} headers -
 * the beginning of the TenantContext propagation described in the
 * architecture design's §3, not the whole of it: today those headers are
 * forwarded but only auth-service's own filter reads project scope back
 * out of them (via its own request attributes) - no other service enforces
 * anything based on them yet. That per-service enforcement is separate,
 * larger work (§3's "every service's project-scoped entities get a
 * projectId column and a query filter") and is not implemented here.
 */
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-API-Key";

    public static final String TENANT_ORGANIZATION_ID_ATTRIBUTE = "tenantOrganizationId";
    public static final String TENANT_PROJECT_ID_ATTRIBUTE = "tenantProjectId";
    public static final String TENANT_ENVIRONMENT_ATTRIBUTE = "tenantEnvironment";
    public static final String API_KEY_TYPE_ATTRIBUTE = "apiKeyType";
    public static final String API_KEY_SCOPES_ATTRIBUTE = "apiKeyScopes";

    private final ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceBlockingStub apiKeyValidationStub;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String rawKey = request.getHeader(HEADER_NAME);
        if (rawKey == null || rawKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        ValidateApiKeyResponse validated;
        try {
            validated = apiKeyValidationStub.validateApiKey(
                    ValidateApiKeyRequest.newBuilder().setRawKey(rawKey).build());
        } catch (StatusRuntimeException e) {
            log.warn("API key validation call to auth-service failed: {}", e.getStatus(), e);
            errorResponseWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Service Unavailable", "Could not verify API key (auth-service unreachable)");
            return;
        }

        if (!validated.getValid()) {
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Invalid or revoked API key");
            return;
        }

        request.setAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE, validated.getUserEmail());
        request.setAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE, java.util.Arrays.stream(validated.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());

        if (!validated.getProjectId().isEmpty()) {
            request.setAttribute(TENANT_ORGANIZATION_ID_ATTRIBUTE, validated.getOrganizationId());
            request.setAttribute(TENANT_PROJECT_ID_ATTRIBUTE, validated.getProjectId());
            request.setAttribute(TENANT_ENVIRONMENT_ATTRIBUTE, validated.getEnvironment());
            request.setAttribute(API_KEY_TYPE_ATTRIBUTE, validated.getKeyType());
            if (!validated.getScopes().isEmpty()) {
                request.setAttribute(API_KEY_SCOPES_ATTRIBUTE, validated.getScopes());
            }
        }

        chain.doFilter(request, response);
    }
}
