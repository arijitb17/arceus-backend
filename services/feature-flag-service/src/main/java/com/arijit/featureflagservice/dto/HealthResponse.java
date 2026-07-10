package com.arijit.featureflagservice.dto;

public record HealthResponse(
        String status,
        String databaseStatus,
        String cacheStatus,
        String rabbitMqStatus
) {
}
