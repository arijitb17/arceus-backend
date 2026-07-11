package com.arijit.monitoringservice.controller;

import com.arijit.monitoringservice.dto.HealthResponse;
import com.arijit.monitoringservice.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /health - monitoring-service's own health, per the platform-wide convention every service follows. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Health", description = "monitoring-service's own health (not platform health - see /services and /dashboard for that)")
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    @Operation(summary = "monitoring-service's own health")
    public ResponseEntity<HealthResponse> health() {
        String db = healthService.databaseStatus();
        String rabbit = healthService.rabbitStatus();
        String cache = healthService.cacheStatus();
        String status = "UP".equals(db) && "UP".equals(rabbit) ? "UP" : "DOWN";

        HealthResponse body = new HealthResponse(status, db, cache, rabbit);
        return status.equals("UP")
                ? ResponseEntity.ok(body)
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
