package com.arijit.monitoringservice.dto;

import java.time.Instant;
import java.util.List;

public record DashboardResponse(
        String overallStatus,
        List<ServiceHealthDto> services,
        List<AlertEventResponse> activeAlerts,
        List<AlertEventResponse> recentAlerts,
        PlatformMetricsSummary platformMetrics,
        ResourceUsageSummary resourceUsage,
        Instant generatedAt
) {
}
