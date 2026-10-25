package com.arijit.monitoringservice.dto;

public record ResourceUsageSummary(
        Double avgCpuUsagePercent,
        double totalMemoryUsedMb,
        double totalMemoryMaxMb,
        int totalThreadCount
) {
}
