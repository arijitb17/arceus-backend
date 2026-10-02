package com.arijit.gatewayservice.health;

import com.arijit.arceus.grpc.gateway.AuthGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.NotificationGatewayServiceGrpc;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.AbstractBlockingStub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ServiceHealthChecker {

    private final AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub authGatewayStub;
    private final NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub notificationGatewayStub;
    private final RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub rateLimitStub;

    /** Service name -> "UP" or "DOWN", in a stable, deterministic order. */
    public Map<String, String> checkAll() {
        Map<String, String> statuses = new LinkedHashMap<>();
        statuses.put("auth-service", ping(authGatewayStub, AuthGatewayServiceGrpc.AuthGatewayServiceBlockingStub::ping));
        statuses.put("notification-service", ping(notificationGatewayStub, NotificationGatewayServiceGrpc.NotificationGatewayServiceBlockingStub::ping));
        statuses.put("ratelimit-service", ping(rateLimitStub, RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub::ping));
        return statuses;
    }

    private <S extends AbstractBlockingStub<S>> String ping(S stub, java.util.function.BiFunction<S, PingRequest, ?> pingCall) {
        try {
            S withDeadline = stub.withDeadlineAfter(2, TimeUnit.SECONDS);
            pingCall.apply(withDeadline, PingRequest.newBuilder().build());
            return "UP";
        } catch (StatusRuntimeException ex) {
            return "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
