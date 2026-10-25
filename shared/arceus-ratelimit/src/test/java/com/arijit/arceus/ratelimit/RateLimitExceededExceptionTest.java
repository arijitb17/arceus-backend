package com.arijit.arceus.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitExceededExceptionTest {

    @Test
    void carriesMessageAndRetryAfter() {
        RateLimitExceededException ex = new RateLimitExceededException("slow down", 17);

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("slow down");
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(17);
    }

    @Test
    void rateLimitResultRecordEquality() {
        assertThat(new RateLimitResult(true, 1, 0)).isEqualTo(new RateLimitResult(true, 1, 0));
        assertThat(new RateLimitResult(false, 9, 5).retryAfterSeconds()).isEqualTo(5);
    }
}
