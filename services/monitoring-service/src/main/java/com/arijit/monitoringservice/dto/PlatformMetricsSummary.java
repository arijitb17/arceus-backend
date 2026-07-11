package com.arijit.monitoringservice.dto;

/** Aggregated "Platform Metrics" figure for the GET /dashboard's resource/traffic overview. */
public record PlatformMetricsSummary(
        long totalRequestCount,
        long totalErrorCount,
        Double avgSuccessRate,
        Double avgResponseTimeMs
) {
}
