package com.arijit.monitoringservice.controller;

import com.arijit.monitoringservice.dto.ServiceMetricsDetailResponse;
import com.arijit.monitoringservice.dto.ServiceMetricsDto;
import com.arijit.monitoringservice.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /metrics and GET /metrics/{service} - Metrics Aggregation + Metrics History. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Platform-wide and per-service metrics")
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/metrics")
    @Operation(summary = "Latest metrics for every monitored service")
    public List<ServiceMetricsDto> metrics() {
        return metricsService.getAllLatestMetrics();
    }

    @GetMapping("/metrics/{service}")
    @Operation(summary = "Current metrics plus recent Metrics History for one service")
    public ServiceMetricsDetailResponse serviceMetrics(@PathVariable("service") String service) {
        return metricsService.getServiceMetrics(service);
    }
}
