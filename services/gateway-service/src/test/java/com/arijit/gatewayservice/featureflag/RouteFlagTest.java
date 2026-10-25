package com.arijit.gatewayservice.featureflag;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteFlagTest {

    @Mock FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub stub;
    @Mock RouteFlagGatewayClient clientMock;
    @Mock FilterChain chain;

    @Nested
    class Client {

        private RouteFlagGatewayClient client;

        @BeforeEach
        void setUp() {
            client = new RouteFlagGatewayClient(stub);
            ReflectionTestUtils.setField(client, "environment", "STAGING");
        }

        @Test
        void unknownFlagMeansEnabled() {
            when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.newBuilder().setFound(false).setEnabled(false).build());

            assertThat(client.isRouteEnabled("auth")).isTrue();
        }

        @Test
        void foundFlagIsRespected() {
            when(stub.evaluateFlag(any())).thenReturn(
                    EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(false).build(),
                    EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(true).build());

            assertThat(client.isRouteEnabled("cache")).isFalse();
            assertThat(client.isRouteEnabled("cache")).isTrue();
        }

        @Test
        void failsOpenOnOutage() {
            when(stub.evaluateFlag(any())).thenThrow(new StatusRuntimeException(Status.DEADLINE_EXCEEDED));

            assertThat(client.isRouteEnabled("cache")).isTrue();
        }

        @Test
        void buildsFlagKeyFromRouteName() {
            when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.getDefaultInstance());

            client.isRouteEnabled("scheduler");

            ArgumentCaptor<EvaluateFlagRequest> cap = ArgumentCaptor.forClass(EvaluateFlagRequest.class);
            verify(stub).evaluateFlag(cap.capture());
            assertThat(cap.getValue().getKey()).isEqualTo("gateway.route.scheduler.enabled");
            assertThat(cap.getValue().getEnvironment()).isEqualTo("STAGING");
            assertThat(cap.getValue().getBucketKey()).isEqualTo("scheduler");
            assertThat(cap.getValue().getDefaultValue()).isEqualTo("true");
        }
    }

    @Nested
    class Filter {

        private RouteFlagFilter filter;
        private MockHttpServletResponse response;

        @BeforeEach
        void setUp() {
            filter = new RouteFlagFilter(clientMock, new ErrorResponseWriter());
            response = new MockHttpServletResponse();
        }

        private MockHttpServletRequest req(String path) {
            MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
            r.setRequestURI(path);
            return r;
        }

        @Test
        void enabledRoutePasses() throws Exception {
            when(clientMock.isRouteEnabled("auth")).thenReturn(true);
            MockHttpServletRequest r = req("/api/auth/login");

            filter.doFilter(r, response, chain);

            verify(chain).doFilter(r, response);
        }

        @Test
        void disabledRouteIs503() throws Exception {
            when(clientMock.isRouteEnabled("cache")).thenReturn(false);

            filter.doFilter(req("/api/cache/ns/k"), response, chain);

            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getContentAsString()).contains("temporarily disabled");
            verifyNoInteractions(chain);
        }

        @Test
        void unmappedPathsAreNeverChecked() throws Exception {
            MockHttpServletRequest r = req("/health");

            filter.doFilter(r, response, chain);

            verify(chain).doFilter(r, response);
            verifyNoInteractions(clientMock);
        }

        @Test
        void eachPrefixMapsToItsRouteName() throws Exception {
            when(clientMock.isRouteEnabled(anyString())).thenReturn(true);
            String[][] cases = {{"/api/notifications/x", "notifications"}, {"/api/audit/x", "audit"},
                    {"/api/monitoring/x", "monitoring"}, {"/api/deployments/x", "deployments"},
                    {"/api/scheduler/x", "scheduler"}, {"/api/feature-flags/x", "feature-flags"}};

            for (String[] c : cases) {
                filter.doFilter(req(c[0]), new MockHttpServletResponse(), chain);
                verify(clientMock).isRouteEnabled(c[1]);
            }
        }
    }
}
