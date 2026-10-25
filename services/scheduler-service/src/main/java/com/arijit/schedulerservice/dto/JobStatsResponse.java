package com.arijit.schedulerservice.dto;

import java.util.Map;

public record JobStatsResponse(
        Map<String, Long> countsByStatus,
        long totalJobs,
        int workerPoolSize,
        int workerActiveCount,
        int workerQueueSize,
        long deadLetterCount
) {
}
