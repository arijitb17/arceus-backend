package com.arijit.gatewayservice.ratelimit;

import com.arijit.arceus.grpc.ratelimit.RateLimitCheckRequest;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckResponse;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import com.arijit.gatewayservice.config.GatewayProperties;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Coarse, edge-level limiter applied to every authenticated request before
 * it's forwarded to a downstream service - on top of (not instead of) each
 * service's own fine-grained @RateLimited rules.
 * <p>
 * If ratelimit-service itself is unreachable, this fails OPEN (allows the
 * request through, logging a warning) rather than taking the entire
 * platform down because one dependency hiccupped. Each service's own
 * @RateLimited annotations still apply as a backstop in that case.
 */
@Component
@RequiredArgsConstructor
public class RateLimitGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGatewayClient.class);

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
