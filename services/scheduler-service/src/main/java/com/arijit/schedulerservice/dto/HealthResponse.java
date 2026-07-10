package com.arijit.schedulerservice.dto;

public record HealthResponse(
        String status,
        String schedulerStatus,
        long activeJobs,
        int workerPoolSize,
        int workerActiveCount,
        int workerQueueSize,
        String rabbitMqStatus,
        String databaseStatus
) {
}
