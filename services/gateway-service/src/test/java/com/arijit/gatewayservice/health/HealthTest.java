package com.arijit.gatewayservice.health;

import com.arijit.arceus.grpc.gateway.*;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HealthTest {

    @Mock AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub auth;
    @Mock NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub notification;
    @Mock RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub rateLimit;
    @Mock ServiceHealthChecker checker;

    private ServiceHealthChecker real;

    @BeforeEach
    void setUp() {
        real = new ServiceHealthChecker(auth, notification, rateLimit);
        // withDeadlineAfter returns a derived stub; make each mock return itself
        lenient().when(auth.withDeadlineAfter(2, TimeUnit.SECONDS)).thenReturn(auth);
        lenient().when(notification.withDeadlineAfter(2, TimeUnit.SECONDS)).thenReturn(notification);
        lenient().when(rateLimit.withDeadlineAfter(2, TimeUnit.SECONDS)).thenReturn(rateLimit);
    }

    @Test
    void allServicesUp() {
        when(auth.ping(any())).thenReturn(PingResponse.getDefaultInstance());
        when(notification.ping(any())).thenReturn(PingResponse.getDefaultInstance());
        when(rateLimit.ping(any())).thenReturn(PingResponse.getDefaultInstance());

        assertThat(real.checkAll()).containsEntry("auth-service", "UP")
                .containsEntry("notification-service", "UP").containsEntry("ratelimit-service", "UP");
    }

    @Test
    void grpcFailureMarksOnlyThatServiceDown() {
        when(auth.ping(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        when(notification.ping(any())).thenReturn(PingResponse.getDefaultInstance());
        when(rateLimit.ping(any())).thenThrow(new IllegalStateException("weird"));

        Map<String, String> r = real.checkAll();

        assertThat(r).containsEntry("auth-service", "DOWN").containsEntry("notification-service", "UP")
                .containsEntry("ratelimit-service", "DOWN");
    }

    @Test
    void controllerReportsUpWhenAllUp() throws Exception {
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("auth-service", "UP");
        when(checker.checkAll()).thenReturn(statuses);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(checker)).build();

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.services['auth-service']").value("UP"))
                .andExpect(jsonPath("$.uptime").value(org.hamcrest.Matchers.matchesPattern("\\d+h \\d+m \\d+s")));
    }

    @Test
    void controllerDegradedWhenAnyDown() throws Exception {
        when(checker.checkAll()).thenReturn(Map.of("auth-service", "DOWN", "ratelimit-service", "UP"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(checker)).build();

        mvc.perform(get("/health")).andExpect(jsonPath("$.status").value("DEGRADED"));
    }
}
