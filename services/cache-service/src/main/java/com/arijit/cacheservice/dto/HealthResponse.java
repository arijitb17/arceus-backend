package com.arijit.cacheservice.dto;

public record HealthResponse(
        String status,
        String redisStatus,
        String connectionPoolStatus,
        long memoryUsageBytes
) {
}
