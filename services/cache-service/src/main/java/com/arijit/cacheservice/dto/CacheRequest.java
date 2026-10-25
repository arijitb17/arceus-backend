package com.arijit.cacheservice.dto;

import java.time.Instant;

public record CacheRequest(
        String namespace,

        String key,

        Object value,

        CacheDataType type,

        SerializationFormat serialization,

        Boolean compress,

        TtlPolicy ttlPolicy,

        Long ttlSeconds,

        Instant absoluteExpireAt,

        CachePolicy cachePolicy
) {
    public CacheDataType typeOrDefault() {
        return type != null ? type : CacheDataType.STRING;
    }

    public boolean compressOrDefault() {
        return compress != null && compress;
    }

    public TtlPolicy ttlPolicyOrDefault() {
        return ttlPolicy != null ? ttlPolicy : TtlPolicy.NO_EXPIRY;
    }

    public CachePolicy cachePolicyOrDefault() {
        return cachePolicy != null ? cachePolicy : CachePolicy.CACHE_ASIDE;
    }

    public CacheRequest withNamespaceAndKey(String ns, String k) {
        return new CacheRequest(ns, k, value, type, serialization, compress, ttlPolicy, ttlSeconds, absoluteExpireAt, cachePolicy);
    }
}
