package com.arijit.gatewayservice.proxy;

import com.arijit.arceus.grpc.gateway.HttpHeader;
import com.arijit.arceus.grpc.gateway.HttpRequestMessage;
import com.arijit.arceus.grpc.gateway.HttpResponseMessage;
import com.google.protobuf.ByteString;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;

/**
 * Translates between the servlet world (HttpServletRequest/Response) that
 * clients speak and the generic HttpRequestMessage/HttpResponseMessage
 * envelope that travels over gRPC to every downstream service. Keeping this
 * in one place means ProxyController stays focused on routing decisions.
 */
@Component
public class HttpMessageMapper {

    // Hop-by-hop / connection-specific headers that must never be replayed
    // downstream or back to the client - the servlet container and RestTemplate
    // recompute their own versions of these.
    private static final Set<String> SKIP_HEADERS = Set.of(
            "content-length", "host", "connection", "transfer-encoding", "keep-alive");

    // Trust headers ONLY the Gateway itself is allowed to set, after real
    // JWT/API-key verification - stripped from whatever the client sent
    // BEFORE re-adding the Gateway's own authoritative values below. Without
    // this, a client could set its own "X-User-Roles: ROLE_ADMIN" and have
    // it forwarded downstream verbatim (or duplicated alongside the real
    // one), which RoleHeaderAuthFilter in notification-service would then
    // blindly trust. The five X-Tenant-*/X-Api-Key-* headers exist for the
    // same reason once ApiKeyAuthFilter started resolving project scope -
    // see that filter's javadoc.
    private static final Set<String> TRUST_HEADERS = Set.of(
            "x-user-email", "x-user-roles",
            "x-tenant-organization-id", "x-tenant-project-id", "x-tenant-environment",
            "x-api-key-type", "x-api-key-scopes");

    /**
     * Carries the project scope ApiKeyAuthFilter resolved from a project-
     * scoped API key (architecture design §7) - null fields (or a null
     * instance entirely) mean "platform-level request, no project scope",
     * the common case for JWT-authenticated browser traffic. See
     * ApiKeyAuthFilter's javadoc for what's actually enforced downstream
     * today versus what's just propagated for future use.
     */
    public record TenantHeaders(String organizationId, String projectId, String environment, String keyType, String scopes) {}

    public HttpRequestMessage toRequestMessage(HttpServletRequest request, String downstreamPath,
                                                String requestId, String userEmail, java.util.List<String> userRoles) throws IOException {
        return toRequestMessage(request, downstreamPath, requestId, userEmail, userRoles, null);
    }

    public HttpRequestMessage toRequestMessage(HttpServletRequest request, String downstreamPath,
                                                String requestId, String userEmail, java.util.List<String> userRoles,
                                                TenantHeaders tenant) throws IOException {
        HttpRequestMessage.Builder builder = HttpRequestMessage.newBuilder()
                .setRequestId(requestId == null ? "" : requestId)
                .setMethod(request.getMethod())
                .setPath(downstreamPath)
                .setQueryString(request.getQueryString() == null ? "" : request.getQueryString())
                .setBody(ByteString.copyFrom(StreamUtils.copyToByteArray(request.getInputStream())));

        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            Collections.list(headerNames).forEach(name -> {
                if (SKIP_HEADERS.contains(name.toLowerCase()) || TRUST_HEADERS.contains(name.toLowerCase())) {
                    return;
                }
                Collections.list(request.getHeaders(name)).forEach(value ->
                        builder.addHeaders(HttpHeader.newBuilder().setName(name).setValue(value).build()));
            });
        }

        if (userEmail != null && !userEmail.isBlank()) {
            builder.setUserEmail(userEmail);
            // Also carried as a plain header so downstream services' generic
            // gRPC-to-REST adapters don't need any special-case handling to
            // see who the Gateway authenticated this call as.
            builder.addHeaders(HttpHeader.newBuilder().setName("X-User-Email").setValue(userEmail).build());
        }

        if (userRoles != null && !userRoles.isEmpty()) {
            // Not a dedicated proto field (unlike user_email) - deliberately
            // just a generic header, so adding RBAC propagation didn't
            // require touching the .proto contract or regenerating stubs.
            // Downstream services trust this header ONLY because it's only
            // reachable through the internal network the Gateway sits in
            // front of - see each service's RoleHeaderAuthFilter.
            builder.addHeaders(HttpHeader.newBuilder()
                    .setName("X-User-Roles")
                    .setValue(String.join(",", userRoles))
                    .build());
        }

        if (tenant != null) {
            addHeaderIfPresent(builder, "X-Tenant-Organization-Id", tenant.organizationId());
            addHeaderIfPresent(builder, "X-Tenant-Project-Id", tenant.projectId());
            addHeaderIfPresent(builder, "X-Tenant-Environment", tenant.environment());
            addHeaderIfPresent(builder, "X-Api-Key-Type", tenant.keyType());
            addHeaderIfPresent(builder, "X-Api-Key-Scopes", tenant.scopes());
        }

        return builder.build();
    }

    private void addHeaderIfPresent(HttpRequestMessage.Builder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            builder.addHeaders(HttpHeader.newBuilder().setName(name).setValue(value).build());
        }
    }

    public void writeResponse(HttpResponseMessage message, HttpServletResponse response) throws IOException {
        response.setStatus(message.getStatus());
        for (HttpHeader header : message.getHeadersList()) {
            if (SKIP_HEADERS.contains(header.getName().toLowerCase())) {
                continue;
            }
            response.addHeader(header.getName(), header.getValue());
        }
        if (!message.getBody().isEmpty()) {
            response.getOutputStream().write(message.getBody().toByteArray());
        }
    }

    public static String bodyAsUtf8(HttpResponseMessage message) {
        return message.getBody().toString(StandardCharsets.UTF_8);
    }
}
