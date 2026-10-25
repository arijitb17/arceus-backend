package com.arijit.cacheservice;

import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.dto.SerializationFormat;

import java.util.List;

/** Shared builders for tests; keeps the long CacheProperties constructor out of every test. */
public final class CacheTestFixtures {

    private CacheTestFixtures() {}

    public static CacheProperties properties() {
        return properties(5_242_880L, true, 8192L, 3600L);
    }

    public static CacheProperties properties(long maxObjectBytes, boolean compressionEnabled,
                                             long compressionThreshold, long defaultTtlSeconds) {
        return new CacheProperties(
                new CacheProperties.Pool(16, 8, 0, 2000),
                defaultTtlSeconds,
                SerializationFormat.JSON,
                maxObjectBytes,
                new CacheProperties.Compression(compressionEnabled, compressionThreshold),
                new CacheProperties.Namespaces(List.of("session", "app"), true),
                new CacheProperties.Sweep(86_400L),
                new CacheProperties.Internal("internal-token", "http://localhost:8085"),
                new CacheProperties.Scheduling(60_000L, "scheduler-token"));
    }
}
