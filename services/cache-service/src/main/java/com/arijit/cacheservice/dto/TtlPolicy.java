package com.arijit.cacheservice.dto;

/**
 * How long a key lives.
 * <ul>
 *   <li>{@code NO_EXPIRY} - key is persisted; never expires until explicitly deleted/evicted</li>
 *   <li>{@code ABSOLUTE} - expires at a caller-given instant ({@code absoluteExpireAt}), fixed, never renewed</li>
 *   <li>{@code SLIDING} - expires {@code ttlSeconds} after the *last access*; every successful GET renews it</li>
 *   <li>{@code CUSTOM} - expires {@code ttlSeconds} after the write, fixed, never renewed</li>
 * </ul>
 */
public enum TtlPolicy {
    NO_EXPIRY,
    ABSOLUTE,
    SLIDING,
    CUSTOM
}
