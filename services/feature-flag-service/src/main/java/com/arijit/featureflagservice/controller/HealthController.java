package com.arijit.featureflagservice.controller;

import com.arijit.featureflagservice.dto.HealthResponse;
import com.arijit.featureflagservice.service.HealthService;
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
