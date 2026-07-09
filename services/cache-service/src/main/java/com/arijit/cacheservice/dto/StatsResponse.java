package com.arijit.cacheservice.dto;

public record StatsResponse(
        long hitCount,
        long missCount,
        double hitRatio,
        long totalKeys,
        long memoryUsageBytes,
        long expiredKeys,
        long evictionCount
) {
}
