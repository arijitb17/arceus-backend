package com.arijit.monitoringservice.dto;

/** monitoring-service's own health, per the platform-wide GET /health convention every service follows. */
public record HealthResponse(
        String status,
        String databaseStatus,
        String cacheStatus,
        String rabbitStatus
) {
}
