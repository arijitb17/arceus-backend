package com.arijit.monitoringservice.dto;

public record HealthResponse(
        String status,
        String databaseStatus,
        String cacheStatus,
        String rabbitStatus
) {
}
