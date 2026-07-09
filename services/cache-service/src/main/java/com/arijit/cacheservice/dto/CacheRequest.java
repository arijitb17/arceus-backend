package com.arijit.cacheservice.dto;

import java.time.Instant;

/**
 * Body for {@code POST /cache} (create/overwrite) and {@code PUT /cache/{namespace}/{key}}
 * (update-if-exists). Namespace/key are path parameters on the update form, so they're
 * nullable here and filled in by the controller before reaching the service layer.
 *
 * @param namespace        logical namespace, e.g. "session", "otp"; required on POST /cache
 * @param key              cache key within the namespace; required on POST /cache
 * @param value            payload, interpreted per {@link #serialization()} - see {@link CacheDataType}
 *                         for how the shape of this value changes with {@link #type()}
 * @param type             storage type, defaults to STRING
 * @param serialization    defaults to service-wide default (JSON)
 * @param compress         force gzip compression regardless of size; auto-compression above
 *                         the configured threshold happens either way
 * @param ttlPolicy        defaults to NO_EXPIRY
 * @param ttlSeconds        used by SLIDING and CUSTOM
 * @param absoluteExpireAt used by ABSOLUTE
 * @param cachePolicy      defaults to CACHE_ASIDE; recorded for observability only, see {@link CachePolicy}
 */
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

    /** Re-targets this request at a specific namespace/key, e.g. from a path variable. */
    public CacheRequest withNamespaceAndKey(String ns, String k) {
        return new CacheRequest(ns, k, value, type, serialization, compress, ttlPolicy, ttlSeconds, absoluteExpireAt, cachePolicy);
    }
}
