package com.arijit.gatewayservice.ratelimit;

import com.arijit.arceus.grpc.ratelimit.RateLimitCheckRequest;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckResponse;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import com.arijit.gatewayservice.config.GatewayProperties;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import com.arijit.gatewayservice.security.JwtAuthFilter;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterAndClientTest {

    @Mock RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub stub;
    @Mock RateLimitGatewayClient clientMock;
    @Mock FilterChain chain;

    private final GatewayProperties props = new GatewayProperties(new GatewayProperties.Jwt("s"),
            List.of("/health"), new GatewayProperties.RateLimit(100, 60), new GatewayProperties.Cors(List.of()));

    @Nested
    class Client {

        private RateLimitGatewayClient client;

        @BeforeEach
        void setUp() {
            client = new RateLimitGatewayClient(stub, props);
        }

        @Test
        void sendsSlidingWindowRequestWithConfiguredLimits() {
            when(stub.check(any())).thenReturn(RateLimitCheckResponse.newBuilder().setAllowed(true).setRemainingRequests(99).build());

            var d = client.check("user:a@b.com", "/api/cache/x");

            ArgumentCaptor<RateLimitCheckRequest> cap = ArgumentCaptor.forClass(RateLimitCheckRequest.class);
            verify(stub).check(cap.capture());
            assertThat(cap.getValue().getKey()).isEqualTo("gateway:user:a@b.com:/api/cache/x");
            assertThat(cap.getValue().getAlgorithm()).isEqualTo("SLIDING_WINDOW");
            assertThat(cap.getValue().getLimit()).isEqualTo(100);
            assertThat(cap.getValue().getWindowSeconds()).isEqualTo(60);
            assertThat(d.allowed()).isTrue();
            assertThat(d.remainingRequests()).isEqualTo(99);
        }

        @Test
        void relaysDenial() {
            when(stub.check(any())).thenReturn(RateLimitCheckResponse.newBuilder().setAllowed(false).setRetryAfter(12).build());

            var d = client.check("ip:1.1.1.1", "/x");

            assertThat(d.allowed()).isFalse();
            assertThat(d.retryAfter()).isEqualTo(12);
        }

        @Test
        void failsOpenWhenRateLimiterUnreachable() {
            when(stub.check(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            var d = client.check("ip:1.1.1.1", "/x");

            assertThat(d.allowed()).isTrue();
            assertThat(d.remainingRequests()).isEqualTo(100);
            assertThat(d.retryAfter()).isZero();
        }
    }

    @Nested
    class Filter {

        private RateLimitFilter filter;
        private MockHttpServletRequest request;
        private MockHttpServletResponse response;

        @BeforeEach
        void setUp() {
            filter = new RateLimitFilter(props, clientMock, new ErrorResponseWriter());
            request = new MockHttpServletRequest("GET", "/api/cache/x");
            request.setRequestURI("/api/cache/x");
            request.setRemoteAddr("9.9.9.9");
            response = new MockHttpServletResponse();
        }

        @Test
        void publicPathsAreNotRateLimited() throws Exception {
            request.setRequestURI("/health");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            verifyNoInteractions(clientMock);
        }

        @Test
        void anonymousCallersAreKeyedByIp() throws Exception {
            when(clientMock.check("ip:9.9.9.9", "/api/cache/x"))
                    .thenReturn(new RateLimitGatewayClient.RateLimitDecision(true, 7, 0));

            filter.doFilter(request, response, chain);

            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("7");
            verify(chain).doFilter(request, response);
        }

        @Test
        void authenticatedCallersAreKeyedByUser() throws Exception {
            request.setAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE, "a@b.com");
            when(clientMock.check("user:a@b.com", "/api/cache/x"))
                    .thenReturn(new RateLimitGatewayClient.RateLimitDecision(true, 1, 0));

            filter.doFilter(request, response, chain);

            verify(clientMock).check("user:a@b.com", "/api/cache/x");
        }

        @Test
        void deniedRequestGets429WithRetryAfterAndStopsChain() throws Exception {
            when(clientMock.check(anyString(), anyString()))
                    .thenReturn(new RateLimitGatewayClient.RateLimitDecision(false, 0, 30));

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(429);
            assertThat(response.getHeader("Retry-After")).isEqualTo("30");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
            assertThat(response.getContentAsString()).contains("Rate limit exceeded");
            verifyNoInteractions(chain);
        }
    }
}
