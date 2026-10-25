package com.arijit.monitoringservice.dto;

public record PlatformMetricsSummary(
        long totalRequestCount,
        long totalErrorCount,
        Double avgSuccessRate,
        Double avgResponseTimeMs
) {
}
