package com.arijit.cacheservice.dto;

import java.time.Instant;

/**
 * Returned by GET and by any write operation that echoes back the stored
 * entry. {@code ttlSecondsRemaining} follows Redis TTL conventions:
 * -1 means "no expiry", -2 means "key not found" (only used if callers
 * fetch TTL through a path that tolerates a miss).
 */
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
