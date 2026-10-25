package com.arijit.ratelimitservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckRequest;
import com.arijit.arceus.grpc.ratelimit.RateLimitCheckResponse;
import com.arijit.ratelimitservice.service.SlidingWindowLimiter;
import com.arijit.ratelimitservice.service.TokenBucketLimiter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitGrpcServiceImplTest {

    @Mock SlidingWindowLimiter sliding;
    @Mock TokenBucketLimiter bucket;
    @Mock StreamObserver<RateLimitCheckResponse> observer;
    @Mock StreamObserver<PingResponse> pingObserver;
    @InjectMocks RateLimitGrpcServiceImpl grpc;

    private com.arijit.ratelimitservice.dto.RateLimitCheckResponse result(boolean allowed, long remaining, long retry) {
        return new com.arijit.ratelimitservice.dto.RateLimitCheckResponse(allowed, remaining, retry);
    }

    private RateLimitCheckResponse response() {
        ArgumentCaptor<RateLimitCheckResponse> cap = ArgumentCaptor.forClass(RateLimitCheckResponse.class);
        verify(observer).onNext(cap.capture());
        verify(observer).onCompleted();
        return cap.getValue();
    }

    private Status invalidArgument() {
        ArgumentCaptor<Throwable> cap = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(cap.capture());
        return ((StatusRuntimeException) cap.getValue()).getStatus();
    }

    @Test
    void blankAlgorithmDefaultsToSlidingWindow() {
        when(sliding.tryConsume("k", 5, 60)).thenReturn(result(true, 4, 0));

        grpc.check(RateLimitCheckRequest.newBuilder().setKey("k").setLimit(5).setWindowSeconds(60).build(), observer);

        RateLimitCheckResponse r = response();
        assertThat(r.getAllowed()).isTrue();
        assertThat(r.getRemainingRequests()).isEqualTo(4);
        verifyNoInteractions(bucket);
    }

    @Test
    void slidingWindowWithoutWindowIsInvalid() {
        grpc.check(RateLimitCheckRequest.newBuilder().setKey("k").setAlgorithm("SLIDING_WINDOW").setLimit(5).build(), observer);

        assertThat(invalidArgument().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(sliding);
    }

    @Test
    void tokenBucketUsesCapacityThenLimit() {
        when(bucket.tryConsume("k", 12, 3.0)).thenReturn(result(false, 0, 2));
        when(bucket.tryConsume("k2", 7, 1.5)).thenReturn(result(true, 6, 0));

        grpc.check(RateLimitCheckRequest.newBuilder().setKey("k").setAlgorithm("TOKEN_BUCKET")
                .setCapacity(12).setLimit(99).setRefillPerSecond(3.0).build(), observer);
        RateLimitCheckResponse first = response();
        assertThat(first.getAllowed()).isFalse();
        assertThat(first.getRetryAfter()).isEqualTo(2);

        StreamObserver<RateLimitCheckResponse> second = mock(StreamObserver.class);
        grpc.check(RateLimitCheckRequest.newBuilder().setKey("k2").setAlgorithm("TOKEN_BUCKET")
                .setLimit(7).setRefillPerSecond(1.5).build(), second);
        verify(second).onCompleted();
    }

    @Test
    void tokenBucketWithoutRefillIsInvalid() {
        grpc.check(RateLimitCheckRequest.newBuilder().setKey("k").setAlgorithm("TOKEN_BUCKET").setCapacity(5).build(), observer);

        assertThat(invalidArgument().getDescription()).contains("refillPerSecond");
    }

    @Test
    void unknownAlgorithmNameThrowsIllegalArgument() {
        assertThatThrownBy(() -> grpc.check(RateLimitCheckRequest.newBuilder().setKey("k").setAlgorithm("LEAKY").build(), observer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ping() {
        grpc.ping(PingRequest.getDefaultInstance(), pingObserver);

        ArgumentCaptor<PingResponse> cap = ArgumentCaptor.forClass(PingResponse.class);
        verify(pingObserver).onNext(cap.capture());
        assertThat(cap.getValue().getService()).isEqualTo("ratelimit-service");
    }
}
