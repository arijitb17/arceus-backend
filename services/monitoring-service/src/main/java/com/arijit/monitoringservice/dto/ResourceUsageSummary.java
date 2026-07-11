package com.arijit.monitoringservice.dto;

/** Aggregated "Resource Usage" figure for the GET /dashboard's resource overview. */
public record ResourceUsageSummary(
        Double avgCpuUsagePercent,
        double totalMemoryUsedMb,
        double totalMemoryMaxMb,
        int totalThreadCount
) {
}
