package com.arijit.monitoringservice.dto;

import java.time.Instant;
import java.util.List;

/**
 * GET /dashboard - everything the spec's Health Dashboard section asks to
 * display in one call: Overall Platform Status, Individual Service Status,
 * Service Uptime (via each ServiceHealthDto.uptimeSeconds), Recent Alerts,
 * Active Alerts, Platform Metrics, Resource Usage.
 */
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
