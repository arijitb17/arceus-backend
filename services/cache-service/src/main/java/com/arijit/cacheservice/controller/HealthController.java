package com.arijit.cacheservice.controller;

import com.arijit.cacheservice.dto.HealthResponse;
import com.arijit.cacheservice.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health", description = "Redis connectivity, connection pool status, and memory usage")
public class HealthController {
    private final HealthService healthService;

    @GetMapping("/health")
    @Operation(summary = "Redis status, connection pool status, memory usage")
    public HealthResponse health() {
        return healthService.check();
    }
}
