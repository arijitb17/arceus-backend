package com.arijit.gatewayservice.ratelimit;

import com.arijit.arceus.grpc.ratelimit.RateLimitCheckRequest;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckResponse;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import com.arijit.gatewayservice.config.GatewayProperties;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitGatewayClient {

    private final RateLimitGrpcServiceGrpc.RateLimitGrpcServiceBlockingStub rateLimitStub;
    private final GatewayProperties properties;

    public RateLimitDecision check(String identity, String path) {
        String key = "gateway:%s:%s".formatted(identity, path);

        RateLimitCheckRequest request = RateLimitCheckRequest.newBuilder()
                .setKey(key)
                .setAlgorithm("SLIDING_WINDOW")
                .setLimit(properties.rateLimit().limit())
                .setWindowSeconds(properties.rateLimit().windowSeconds())
                .build();

        try {
            RateLimitCheckResponse response = rateLimitStub.check(request);
            return new RateLimitDecision(response.getAllowed(), response.getRemainingRequests(), response.getRetryAfter());
        } catch (StatusRuntimeException ex) {
            log.warn("RateLimiter Service unreachable ({}), failing open for key={}", ex.getStatus(), key);
            return RateLimitDecision.allowedFailOpen(properties.rateLimit().limit());
        }
    }

    public record RateLimitDecision(boolean allowed, long remainingRequests, long retryAfter) {
        static RateLimitDecision allowedFailOpen(long limit) {
            return new RateLimitDecision(true, limit, 0);
        }
    }
}
