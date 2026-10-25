package com.arijit.ratelimitservice.controller;

import com.arijit.ratelimitservice.dto.RateLimitCheckRequest;
import com.arijit.ratelimitservice.dto.RateLimitCheckResponse;
import com.arijit.ratelimitservice.exception.ApiException;
import com.arijit.ratelimitservice.service.SlidingWindowLimiter;
import com.arijit.ratelimitservice.service.TokenBucketLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Rate Limiter", description = "Ask whether a call identified by `key` is allowed right now")
public class RateLimitController {
    private final SlidingWindowLimiter slidingWindowLimiter;
    private final TokenBucketLimiter tokenBucketLimiter;

    @PostMapping("/check")
    @Operation(summary = "Check + consume one unit of allowance for `key`, using either SLIDING_WINDOW or TOKEN_BUCKET")
    public ResponseEntity<RateLimitCheckResponse> check(@Valid @RequestBody RateLimitCheckRequest request) {
        RateLimitCheckResponse response = switch (request.algorithmOrDefault()) {
            case SLIDING_WINDOW -> {
                if (request.limit() == null || request.windowSeconds() == null) {
                    throw new ApiException("SLIDING_WINDOW requires both 'limit' and 'windowSeconds'", HttpStatus.BAD_REQUEST);
                }
                yield slidingWindowLimiter.tryConsume(request.key(), request.limit(), request.windowSeconds());
            }
            case TOKEN_BUCKET -> {
                Integer capacity = request.capacity() != null ? request.capacity() : request.limit();
                if (capacity == null || request.refillPerSecond() == null) {
                    throw new ApiException("TOKEN_BUCKET requires 'capacity' (or 'limit') and 'refillPerSecond'", HttpStatus.BAD_REQUEST);
                }
                yield tokenBucketLimiter.tryConsume(request.key(), capacity, request.refillPerSecond());
            }
        };
        return ResponseEntity.ok(response);
    }
}
