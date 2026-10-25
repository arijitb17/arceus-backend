package com.arijit.ratelimitservice.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitCheckRequestTest {

    @Test
    void algorithmDefaultsToSlidingWindow() {
        assertThat(new RateLimitCheckRequest("k", null, 1, 1, null, null).algorithmOrDefault())
                .isEqualTo(RateLimitAlgorithm.SLIDING_WINDOW);
    }

    @Test
    void explicitAlgorithmKept() {
        assertThat(new RateLimitCheckRequest("k", RateLimitAlgorithm.TOKEN_BUCKET, null, null, 1, 1.0).algorithmOrDefault())
                .isEqualTo(RateLimitAlgorithm.TOKEN_BUCKET);
    }
}
