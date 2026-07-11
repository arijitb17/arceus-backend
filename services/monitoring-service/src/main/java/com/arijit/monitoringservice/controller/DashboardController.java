package com.arijit.monitoringservice.controller;

import com.arijit.monitoringservice.dto.DashboardResponse;
import com.arijit.monitoringservice.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /dashboard - the Health Dashboard, one call with everything the spec asks it to display. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Platform Dashboard API")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "Full platform dashboard: overall status, per-service status, alerts, aggregated metrics")
    public DashboardResponse dashboard() {
        return dashboardService.getDashboard();
    }
}
