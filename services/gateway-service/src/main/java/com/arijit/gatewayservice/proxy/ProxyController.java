package com.arijit.gatewayservice.proxy;

import com.arijit.arceus.grpc.gateway.AuditGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.AuthGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.HttpRequestMessage;
import com.arijit.arceus.grpc.gateway.HttpResponseMessage;
import com.arijit.arceus.grpc.gateway.NotificationGatewayServiceGrpc;
import com.arijit.gatewayservice.error.ApiException;
import com.arijit.gatewayservice.filter.RequestIdFilter;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * The only REST surface clients ever talk to for Auth, Notification, and
 * Audit. Everything under /api/&lt;service&gt;/** is:
 * <ol>
 *   <li>matched to a downstream service via {@link Route},</li>
 *   <li>rewritten from the client-facing path to that service's own
 *       internal REST path (they don't all share the /api/&lt;service&gt;
 *       prefix - notification-service and audit-service predate the
 *       Gateway and keep their original paths),</li>
 *   <li>packed into a generic HttpRequestMessage and sent over gRPC,</li>
 *   <li>and the HttpResponseMessage that comes back is replayed onto the
 *       client's HttpServletResponse as-is.</li>
 * </ol>
 * JWT validation and rate limiting already happened in filters before this
 * controller is ever reached; RateLimiter Service itself and the
 * /health endpoint are not proxied through here.
 */
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private enum Route {
        AUTH("/api/auth", "/api/auth"),
        NOTIFICATIONS("/api/notifications", "/notifications"),
        AUDIT("/api/audit", "/audit-logs");

        final String gatewayPrefix;
        final String downstreamPrefix;

        Route(String gatewayPrefix, String downstreamPrefix) {
            this.gatewayPrefix = gatewayPrefix;
            this.downstreamPrefix = downstreamPrefix;
        }

        String toDownstreamPath(String requestUri) {
            String remainder = requestUri.substring(gatewayPrefix.length());
            return downstreamPrefix + remainder;
        }
    }

    private final HttpMessageMapper httpMessageMapper;
    private final AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub authGatewayStub;
    private final NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub notificationGatewayStub;
    private final AuditGatewayServiceGrpc.AuditGatewayServiceBlockingStub auditGatewayStub;

    @RequestMapping("/api/auth/**")
    public void auth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.AUTH, authGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/notifications/**")
    public void notifications(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.NOTIFICATIONS, notificationGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/audit/**")
    public void audit(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.AUDIT, auditGatewayStub::forward, request, response);
    }

    private void forward(Route route, Function<HttpRequestMessage, HttpResponseMessage> call,
                          HttpServletRequest request, HttpServletResponse response) throws IOException {

        String downstreamPath = route.toDownstreamPath(request.getRequestURI());
        String requestId = String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        Object userEmail = request.getAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE);
        @SuppressWarnings("unchecked")
        java.util.List<String> userRoles = (java.util.List<String>) request.getAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE);

        HttpRequestMessage message = httpMessageMapper.toRequestMessage(
                request, downstreamPath, requestId, userEmail == null ? null : userEmail.toString(), userRoles);

        try {
            HttpResponseMessage result = call.apply(message);
            httpMessageMapper.writeResponse(result, response);
        } catch (StatusRuntimeException ex) {
            // The gRPC channel itself failed (service down, connection refused,
            // deadline exceeded) - distinct from the downstream service returning
            // a normal non-2xx REST response, which arrives as a regular
            // HttpResponseMessage and is just replayed above.
            throw new ApiException(
                    "Upstream service is currently unavailable (%s)".formatted(ex.getStatus().getCode()),
                    HttpStatus.BAD_GATEWAY);
        }
    }
}
