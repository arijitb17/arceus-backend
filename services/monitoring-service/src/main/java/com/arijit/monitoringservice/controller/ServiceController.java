package com.arijit.monitoringservice.controller;

import com.arijit.monitoringservice.dto.PlatformHealthResponse;
import com.arijit.monitoringservice.dto.ServiceDetailResponse;
import com.arijit.monitoringservice.service.PlatformHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Services", description = "Platform-wide and per-service health status")
public class ServiceController {
    private final PlatformHealthService platformHealthService;

    @GetMapping("/services")
    @Operation(summary = "Latest health status of every monitored service")
    public PlatformHealthResponse services() {
        return platformHealthService.getPlatformHealth();
    }

    @GetMapping("/services/{service}")
    @Operation(summary = "Current status plus recent Service Status History for one service")
    public ServiceDetailResponse service(@PathVariable("service") String service) {
        return platformHealthService.getServiceDetail(service);
    }
}
