package com.arijit.schedulerservice.controller;

import com.arijit.schedulerservice.dto.HealthResponse;
import com.arijit.schedulerservice.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Health", description = "Scheduler status, worker pool, and broker/database connectivity")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    @Operation(summary = "Scheduler status, active jobs, queue size, worker status, RabbitMQ status")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(healthService.check());
    }
}
