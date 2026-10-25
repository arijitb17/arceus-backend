package com.arijit.cacheservice.dto;

import java.time.Instant;

public record CacheResponse(
        String namespace,
        String key,
        Object value,
        CacheDataType type,
        SerializationFormat serialization,
        TtlPolicy ttlPolicy,
        long ttlSecondsRemaining,
        Instant createdAt,
        Instant updatedAt
) {
}
