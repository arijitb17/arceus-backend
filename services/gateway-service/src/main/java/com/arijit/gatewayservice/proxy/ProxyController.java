package com.arijit.gatewayservice.proxy;

import com.arijit.arceus.grpc.gateway.AuthGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.CacheGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.FeatureFlagGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.HttpRequestMessage;
import com.arijit.arceus.grpc.gateway.HttpResponseMessage;
import com.arijit.arceus.grpc.gateway.MonitoringGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.NotificationGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.OrganizationGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.SchedulerGatewayServiceGrpc;
import com.arijit.gatewayservice.error.ApiException;
import com.arijit.gatewayservice.filter.RequestIdFilter;
import com.arijit.gatewayservice.security.ApiKeyAuthFilter;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import com.arijit.gatewayservice.usage.UsageEventPublisher;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProxyController {
    private enum Route {
        AUTH("/api/auth", "/api/auth"),
        NOTIFICATIONS("/api/notifications", "/notifications"),
        MONITORING("/api/monitoring", ""),
        SCHEDULER("/api/scheduler", "/jobs"),
        FEATURE_FLAGS("/api/feature-flags", "/flags"),
        CACHE("/api/cache", "/cache"),
        ORGANIZATIONS("/api/organizations", "/organizations");

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
    private final UsageEventPublisher usageEventPublisher;
    private final AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub authGatewayStub;
    private final NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub notificationGatewayStub;
    private final MonitoringGatewayServiceGrpc.MonitoringGatewayServiceBlockingStub monitoringGatewayStub;
    private final SchedulerGatewayServiceGrpc.SchedulerGatewayServiceBlockingStub schedulerGatewayStub;
    private final FeatureFlagGatewayServiceGrpc.FeatureFlagGatewayServiceBlockingStub featureFlagGatewayStub;
    private final CacheGatewayServiceGrpc.CacheGatewayServiceBlockingStub cacheGatewayStub;
    private final OrganizationGatewayServiceGrpc.OrganizationGatewayServiceBlockingStub organizationGatewayStub;

    @RequestMapping("/api/auth/**")
    public void auth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.AUTH, authGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/notifications/**")
    public void notifications(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.NOTIFICATIONS, notificationGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/monitoring/**")
    public void monitoring(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.MONITORING, monitoringGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/scheduler/**")
    public void scheduler(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.SCHEDULER, schedulerGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/feature-flags/**")
    public void featureFlags(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.FEATURE_FLAGS, featureFlagGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/cache/**")
    public void cache(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.CACHE, cacheGatewayStub::forward, request, response);
    }

    @RequestMapping("/api/organizations/**")
    public void organizations(HttpServletRequest request, HttpServletResponse response) throws IOException {
        forward(Route.ORGANIZATIONS, organizationGatewayStub::forward, request, response);
    }

    private void forward(Route route, Function<HttpRequestMessage, HttpResponseMessage> call,
                          HttpServletRequest request, HttpServletResponse response) throws IOException {
        String downstreamPath = route.toDownstreamPath(request.getRequestURI());
        String requestId = String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
        Object userEmail = request.getAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE);
        @SuppressWarnings("unchecked")
        java.util.List<String> userRoles = (java.util.List<String>) request.getAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE);

        HttpMessageMapper.TenantHeaders tenant = new HttpMessageMapper.TenantHeaders(
                stringAttr(request, ApiKeyAuthFilter.TENANT_ORGANIZATION_ID_ATTRIBUTE),
                stringAttr(request, ApiKeyAuthFilter.TENANT_PROJECT_ID_ATTRIBUTE),
                stringAttr(request, ApiKeyAuthFilter.TENANT_ENVIRONMENT_ATTRIBUTE),
                stringAttr(request, ApiKeyAuthFilter.API_KEY_TYPE_ATTRIBUTE),
                stringAttr(request, ApiKeyAuthFilter.API_KEY_SCOPES_ATTRIBUTE));

        HttpRequestMessage message = httpMessageMapper.toRequestMessage(
                request, downstreamPath, requestId, userEmail == null ? null : userEmail.toString(), userRoles, tenant);

        try {
            HttpResponseMessage result = call.apply(message);
            httpMessageMapper.writeResponse(result, response);

            if (tenant.projectId() != null) {
                usageEventPublisher.recordRequest(tenant.organizationId(), tenant.projectId(), tenant.environment(),
                        route.name(), result.getStatus());
            }
        } catch (StatusRuntimeException ex) {
            log.error("Upstream {} unavailable for {} {} (requestId={}): {}",
                    route, request.getMethod(), downstreamPath, requestId, ex.getStatus(), ex);
            throw new ApiException(
                    "Upstream service is currently unavailable (%s)".formatted(ex.getStatus().getCode()),
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private String stringAttr(HttpServletRequest request, String attributeName) {
        Object value = request.getAttribute(attributeName);
        return value == null ? null : value.toString();
    }
}
