package com.arijit.schedulerservice.dto;

import java.util.Map;

/**
 * GET /jobs/stats - a live snapshot, not a historical aggregate. {@code
 * countsByStatus} covers every {@link com.arijit.schedulerservice.entity.JobStatus}
 * that has at least one row; a status with zero jobs is simply absent
 * rather than present with a 0, so callers should treat a missing key as 0.
 */
public record JobStatsResponse(
        Map<String, Long> countsByStatus,
        long totalJobs,
        int workerPoolSize,
        int workerActiveCount,
        int workerQueueSize,
        long deadLetterCount
) {
}
