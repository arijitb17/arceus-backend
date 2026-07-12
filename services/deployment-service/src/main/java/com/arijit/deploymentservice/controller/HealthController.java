package com.arijit.deploymentservice.controller;

import com.arijit.deploymentservice.dto.HealthResponse;
import com.arijit.deploymentservice.service.HealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping("/health")
    public HealthResponse health() {
        return healthService.check();
    }
}
