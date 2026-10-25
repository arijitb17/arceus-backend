package com.arijit.ratelimitservice.controller;

import com.arijit.ratelimitservice.dto.RateLimitCheckResponse;
import com.arijit.ratelimitservice.exception.GlobalExceptionHandler;
import com.arijit.ratelimitservice.service.SlidingWindowLimiter;
import com.arijit.ratelimitservice.service.TokenBucketLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RateLimitControllerTest {

    @Mock SlidingWindowLimiter sliding;
    @Mock TokenBucketLimiter bucket;
    @InjectMocks RateLimitController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private org.springframework.test.web.servlet.ResultActions check(String json) throws Exception {
        return mvc.perform(post("/check").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void defaultsToSlidingWindow() throws Exception {
        when(sliding.tryConsume("k", 10, 60)).thenReturn(new RateLimitCheckResponse(true, 9, 0));

        check("{\"key\":\"k\",\"limit\":10,\"windowSeconds\":60}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remainingRequests").value(9));
        verifyNoInteractions(bucket);
    }

    @Test
    void slidingWindowMissingParamsIs400() throws Exception {
        check("{\"key\":\"k\",\"algorithm\":\"SLIDING_WINDOW\",\"limit\":10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SLIDING_WINDOW requires both 'limit' and 'windowSeconds'"));
        verifyNoInteractions(sliding);
    }

    @Test
    void tokenBucketUsesCapacityWhenGiven() throws Exception {
        when(bucket.tryConsume("k", 20, 2.5)).thenReturn(new RateLimitCheckResponse(false, 0, 4));

        check("{\"key\":\"k\",\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":20,\"limit\":5,\"refillPerSecond\":2.5}")
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfter").value(4));
    }

    @Test
    void tokenBucketFallsBackToLimitAsCapacity() throws Exception {
        when(bucket.tryConsume("k", 5, 1.0)).thenReturn(new RateLimitCheckResponse(true, 4, 0));

        check("{\"key\":\"k\",\"algorithm\":\"TOKEN_BUCKET\",\"limit\":5,\"refillPerSecond\":1.0}")
                .andExpect(status().isOk());
    }

    @Test
    void tokenBucketMissingRefillIs400() throws Exception {
        check("{\"key\":\"k\",\"algorithm\":\"TOKEN_BUCKET\",\"capacity\":5}").andExpect(status().isBadRequest());
    }

    @Test
    void bodyValidation() throws Exception {
        check("{\"key\":\"\",\"limit\":-1,\"windowSeconds\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.key").value("key is required"))
                .andExpect(jsonPath("$.fields.limit").value("limit must be positive"))
                .andExpect(jsonPath("$.fields.windowSeconds").value("windowSeconds must be positive"));
    }

    @Test
    void unexpectedFailureIs500WithoutLeak() throws Exception {
        when(sliding.tryConsume(any(), anyInt(), anyInt())).thenThrow(new IllegalStateException("redis password"));

        check("{\"key\":\"k\",\"limit\":1,\"windowSeconds\":1}")
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }
}
