package com.arijit.ratelimitservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckRequest;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckResponse;
import com.arijit.arceus.grpc.ratelimit.RateLimitGrpcServiceGrpc;
import com.arijit.ratelimitservice.dto.RateLimitAlgorithm;
import com.arijit.ratelimitservice.exception.ApiException;
import com.arijit.ratelimitservice.service.SlidingWindowLimiter;
import com.arijit.ratelimitservice.service.TokenBucketLimiter;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RateLimitGrpcServiceImpl extends RateLimitGrpcServiceGrpc.RateLimitGrpcServiceImplBase {
    private final SlidingWindowLimiter slidingWindowLimiter;
    private final TokenBucketLimiter tokenBucketLimiter;

    @Override
    public void check(RateLimitCheckRequest request, StreamObserver<RateLimitCheckResponse> responseObserver) {
        try {
            com.arijit.ratelimitservice.dto.RateLimitCheckResponse result = switch (algorithmOrDefault(request)) {
                case SLIDING_WINDOW -> {
                    if (request.getLimit() <= 0 || request.getWindowSeconds() <= 0) {
                        throw new ApiException("SLIDING_WINDOW requires both 'limit' and 'windowSeconds'",
                                org.springframework.http.HttpStatus.BAD_REQUEST);
                    }
                    yield slidingWindowLimiter.tryConsume(request.getKey(), request.getLimit(), request.getWindowSeconds());
                }
                case TOKEN_BUCKET -> {
                    int capacity = request.getCapacity() > 0 ? request.getCapacity() : request.getLimit();
                    if (capacity <= 0 || request.getRefillPerSecond() <= 0) {
                        throw new ApiException("TOKEN_BUCKET requires 'capacity' (or 'limit') and 'refillPerSecond'",
                                org.springframework.http.HttpStatus.BAD_REQUEST);
                    }
                    yield tokenBucketLimiter.tryConsume(request.getKey(), capacity, request.getRefillPerSecond());
                }
            };

            if (!result.allowed()) {
                log.debug("Rate limit denied: algorithm={} retryAfter={}", request.getAlgorithm(), result.retryAfter());
            }
            responseObserver.onNext(RateLimitCheckResponse.newBuilder()
                    .setAllowed(result.allowed())
                    .setRemainingRequests(result.remainingRequests())
                    .setRetryAfter(result.retryAfter())
                    .build());
            responseObserver.onCompleted();
        } catch (ApiException ex) {
            log.warn("Rate limit check rejected as invalid: {}", ex.getMessage());
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(ex.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        responseObserver.onNext(PingResponse.newBuilder().setOk(true).setService("ratelimit-service").build());
        responseObserver.onCompleted();
    }

    private RateLimitAlgorithm algorithmOrDefault(RateLimitCheckRequest request) {
        if (request.getAlgorithm() == null || request.getAlgorithm().isBlank()) {
            return RateLimitAlgorithm.SLIDING_WINDOW;
        }
        return RateLimitAlgorithm.valueOf(request.getAlgorithm());
    }
}
