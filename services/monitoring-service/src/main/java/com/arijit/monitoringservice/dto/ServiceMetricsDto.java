package com.arijit.monitoringservice.dto;

import java.time.Instant;

public record ServiceMetricsDto(
        String serviceName,
        Long requestCount,
        Long errorCount,
        Double successRate,
        Double avgResponseTimeMs,
        Double cpuUsagePercent,
        Double memoryUsedMb,
        Double memoryMaxMb,
        Double jvmHeapUsedMb,
        Integer threadCount,
        Integer activeConnections,
        Integer queueSize,
        Double cacheHitRatio,
        Double cacheMissRatio,
        Integer schedulerActiveJobs,
        Integer schedulerFailedJobs,
        Long featureFlagEvaluations,
        Long rateLimitRequests,
        Long rateLimitBlocked,
        Instant capturedAt
) {
}
