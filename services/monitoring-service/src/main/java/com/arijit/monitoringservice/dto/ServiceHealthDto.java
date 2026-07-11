package com.arijit.monitoringservice.dto;

import java.time.Instant;

public record ServiceHealthDto(
        String serviceName,
        String status,
        String databaseStatus,
        String cacheStatus,
        String rabbitStatus,
        String grpcStatus,
        boolean apiAvailable,
        long responseTimeMs,
        long uptimeSeconds,
        String version,
        String errorMessage,
        Instant checkedAt
) {
}
