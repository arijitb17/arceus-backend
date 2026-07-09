package com.arijit.cacheservice.core;

import com.arijit.cacheservice.dto.CacheDataType;
import com.arijit.cacheservice.dto.CachePolicy;
import com.arijit.cacheservice.dto.SerializationFormat;
import com.arijit.cacheservice.dto.TtlPolicy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Small companion record stored as a Redis hash next to every entry's data
 * key. Exists because a bare GET-by-key has no other way to know which
 * native structure to read from, how the bytes were serialized/compressed,
 * or (for {@link TtlPolicy#SLIDING}) how many seconds to re-arm the TTL for
 * on each access.
 */
public record CacheEntryMetadata(
        CacheDataType type,
        SerializationFormat serialization,
        boolean compressed,
        TtlPolicy ttlPolicy,
        long ttlSeconds,
        CachePolicy cachePolicy,
        Instant createdAt,
        Instant updatedAt
) {
    private static final String F_TYPE = "type";
    private static final String F_SERIALIZATION = "serialization";
    private static final String F_COMPRESSED = "compressed";
    private static final String F_TTL_POLICY = "ttlPolicy";
    private static final String F_TTL_SECONDS = "ttlSeconds";
    private static final String F_CACHE_POLICY = "cachePolicy";
    private static final String F_CREATED_AT = "createdAt";
    private static final String F_UPDATED_AT = "updatedAt";

    public Map<String, String> toRedisHash() {
        Map<String, String> hash = new HashMap<>();
        hash.put(F_TYPE, type.name());
        hash.put(F_SERIALIZATION, serialization.name());
        hash.put(F_COMPRESSED, String.valueOf(compressed));
        hash.put(F_TTL_POLICY, ttlPolicy.name());
        hash.put(F_TTL_SECONDS, String.valueOf(ttlSeconds));
        hash.put(F_CACHE_POLICY, cachePolicy.name());
        hash.put(F_CREATED_AT, createdAt.toString());
        hash.put(F_UPDATED_AT, updatedAt.toString());
        return hash;
    }

    public static CacheEntryMetadata fromRedisHash(Map<Object, Object> hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        return new CacheEntryMetadata(
                CacheDataType.valueOf(str(hash, F_TYPE)),
                SerializationFormat.valueOf(str(hash, F_SERIALIZATION)),
                Boolean.parseBoolean(str(hash, F_COMPRESSED)),
                TtlPolicy.valueOf(str(hash, F_TTL_POLICY)),
                Long.parseLong(str(hash, F_TTL_SECONDS)),
                CachePolicy.valueOf(str(hash, F_CACHE_POLICY)),
                Instant.parse(str(hash, F_CREATED_AT)),
                Instant.parse(str(hash, F_UPDATED_AT))
        );
    }

    private static String str(Map<Object, Object> hash, String field) {
        Object value = hash.get(field);
        return value == null ? null : value.toString();
    }

    public CacheEntryMetadata withUpdatedNow() {
        return new CacheEntryMetadata(type, serialization, compressed, ttlPolicy, ttlSeconds, cachePolicy, createdAt, Instant.now());
    }
}
