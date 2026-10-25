package com.arijit.cacheservice.controller;

import com.arijit.cacheservice.CacheTestFixtures;
import com.arijit.cacheservice.dto.HealthResponse;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.exception.GlobalExceptionHandler;
import com.arijit.cacheservice.service.CacheSweepService;
import com.arijit.cacheservice.service.HealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SweepAndHealthControllerTest {

    @Mock CacheSweepService sweepService;
    @Mock HealthService healthService;

    private SweepCallbackController sweepController;

    @BeforeEach
    void setUp() {
        sweepController = new SweepCallbackController(sweepService, CacheTestFixtures.properties());
    }

    @Test
    void validTokenRunsSweep() {
        sweepController.sweep("internal-token");

        verify(sweepService).sweepStaleNoExpiryEntries();
    }

    @Test
    void wrongOrMissingTokenIsUnauthorizedAndSkipsSweep() {
        assertThatThrownBy(() -> sweepController.sweep("wrong"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> sweepController.sweep(null)).isInstanceOf(ApiException.class);

        verifyNoInteractions(sweepService);
    }

    @Test
    void sweepEndpointOverHttp() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(sweepController).setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/internal/cache/sweep").param("token", "internal-token")).andExpect(status().isOk());
        mvc.perform(post("/internal/cache/sweep").param("token", "bad")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointReturnsServiceResult() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse("UP", "UP", "UP", 100));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService)).build();

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.memoryUsageBytes").value(100));
    }
}
