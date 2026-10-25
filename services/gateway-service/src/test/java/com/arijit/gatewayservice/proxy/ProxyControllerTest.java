package com.arijit.gatewayservice.proxy;

import com.arijit.arceus.grpc.gateway.*;
import com.arijit.arceus.grpc.featureflag.*;
import com.arijit.arceus.grpc.organization.*;
import com.arijit.gatewayservice.error.ApiException;
import com.arijit.gatewayservice.security.ApiKeyAuthFilter;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import com.arijit.gatewayservice.usage.UsageEventPublisher;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    @Mock UsageEventPublisher usage;
    @Mock AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub auth;
    @Mock NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub notification;
    @Mock MonitoringGatewayServiceGrpc.MonitoringGatewayServiceBlockingStub monitoring;
    @Mock SchedulerGatewayServiceGrpc.SchedulerGatewayServiceBlockingStub scheduler;
    @Mock FeatureFlagGatewayServiceGrpc.FeatureFlagGatewayServiceBlockingStub featureFlags;
    @Mock CacheGatewayServiceGrpc.CacheGatewayServiceBlockingStub cache;
    @Mock OrganizationGatewayServiceGrpc.OrganizationGatewayServiceBlockingStub organizations;

    private ProxyController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        controller = new ProxyController(new HttpMessageMapper(), usage, auth, notification, monitoring, scheduler,
                featureFlags, cache, organizations);
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", uri);
        r.setRequestURI(uri);
        r.setAttribute("requestId", "rid");
        return r;
    }

    private HttpResponseMessage ok(int status) {
        return HttpResponseMessage.newBuilder().setStatus(status).build();
    }

    @Test
    void authRoutePreservesPath() throws Exception {
        when(auth.forward(any())).thenReturn(ok(200));

        controller.auth(request("/api/auth/login"), response);

        ArgumentCaptor<HttpRequestMessage> cap = ArgumentCaptor.forClass(HttpRequestMessage.class);
        verify(auth).forward(cap.capture());
        assertThat(cap.getValue().getPath()).isEqualTo("/api/auth/login");
        assertThat(cap.getValue().getRequestId()).isEqualTo("rid");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void prefixRewriting() throws Exception {
        when(notification.forward(any())).thenReturn(ok(200));
        when(monitoring.forward(any())).thenReturn(ok(200));
        when(scheduler.forward(any())).thenReturn(ok(200));
        when(featureFlags.forward(any())).thenReturn(ok(200));
        when(cache.forward(any())).thenReturn(ok(200));
        when(organizations.forward(any())).thenReturn(ok(200));

        controller.notifications(request("/api/notifications/history"), new MockHttpServletResponse());
        controller.monitoring(request("/api/monitoring/health"), new MockHttpServletResponse());
        controller.scheduler(request("/api/scheduler/42"), new MockHttpServletResponse());
        controller.featureFlags(request("/api/feature-flags/evaluate"), new MockHttpServletResponse());
        controller.cache(request("/api/cache/ns/k"), new MockHttpServletResponse());
        controller.organizations(request("/api/organizations/7/projects"), new MockHttpServletResponse());

        assertThat(pathSentTo(notification)).isEqualTo("/notifications/history");
        assertThat(pathSentTo(monitoring)).isEqualTo("/health");
        assertThat(pathSentTo(scheduler)).isEqualTo("/jobs/42");
        assertThat(pathSentTo(featureFlags)).isEqualTo("/flags/evaluate");
        assertThat(pathSentTo(cache)).isEqualTo("/cache/ns/k");
        assertThat(pathSentTo(organizations)).isEqualTo("/organizations/7/projects");
    }

    private String pathSentTo(Object stub) {
        ArgumentCaptor<HttpRequestMessage> cap = ArgumentCaptor.forClass(HttpRequestMessage.class);
        if (stub == notification) verify(notification).forward(cap.capture());
        else if (stub == monitoring) verify(monitoring).forward(cap.capture());
        else if (stub == scheduler) verify(scheduler).forward(cap.capture());
        else if (stub == featureFlags) verify(featureFlags).forward(cap.capture());
        else if (stub == cache) verify(cache).forward(cap.capture());
        else verify(organizations).forward(cap.capture());
        return cap.getValue().getPath();
    }

    @Test
    void forwardsIdentityAttributes() throws Exception {
        when(auth.forward(any())).thenReturn(ok(200));
        MockHttpServletRequest r = request("/api/auth/me");
        r.setAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE, "a@b.com");
        r.setAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE, List.of("ROLE_USER"));

        controller.auth(r, response);

        ArgumentCaptor<HttpRequestMessage> cap = ArgumentCaptor.forClass(HttpRequestMessage.class);
        verify(auth).forward(cap.capture());
        assertThat(cap.getValue().getUserEmail()).isEqualTo("a@b.com");
        assertThat(cap.getValue().getHeadersList()).anySatisfy(h -> {
            assertThat(h.getName()).isEqualTo("X-User-Roles");
            assertThat(h.getValue()).isEqualTo("ROLE_USER");
        });
    }

    @Test
    void tenantRequestsAreMeteredAfterSuccessfulForward() throws Exception {
        when(cache.forward(any())).thenReturn(ok(201));
        MockHttpServletRequest r = request("/api/cache/ns/k");
        r.setAttribute(ApiKeyAuthFilter.TENANT_ORGANIZATION_ID_ATTRIBUTE, "org");
        r.setAttribute(ApiKeyAuthFilter.TENANT_PROJECT_ID_ATTRIBUTE, "proj");
        r.setAttribute(ApiKeyAuthFilter.TENANT_ENVIRONMENT_ATTRIBUTE, "prod");

        controller.cache(r, response);

        verify(usage).recordRequest("org", "proj", "prod", "CACHE", 201);
    }

    @Test
    void nonTenantRequestsAreNotMetered() throws Exception {
        when(cache.forward(any())).thenReturn(ok(200));

        controller.cache(request("/api/cache/a/b"), response);

        verifyNoInteractions(usage);
    }

    @Test
    void upstreamUnavailableBecomes502() {
        when(auth.forward(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatThrownBy(() -> controller.auth(request("/api/auth/x"), response))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).contains("UNAVAILABLE");
                });
        verify(usage, never()).recordRequest(anyString(), anyString(), anyString(), anyString(), anyInt());
    }
}
