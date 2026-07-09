package com.arijit.cacheservice.dto;

/**
 * The population/invalidation contract the *calling* service follows for
 * this entry. cache-service has no knowledge of any downstream source of
 * truth (that lives in the caller), so this value doesn't change how a
 * write is executed here - it's recorded on the entry's metadata purely for
 * observability/audit (e.g. "why is this namespace mostly write-arounds?"),
 * and is a natural extension point once/if this service grows a
 * write-behind or refresh-ahead feature that needs to know the contract.
 * <ul>
 *   <li>{@code CACHE_ASIDE} (default) - caller checks cache first, loads and populates on miss</li>
 *   <li>{@code WRITE_THROUGH} - caller writes to its store and this cache together, synchronously</li>
 *   <li>{@code WRITE_AROUND} - caller writes only to its store; cache is populated later, on read-miss</li>
 * </ul>
 */
public enum CachePolicy {
    CACHE_ASIDE,
    WRITE_THROUGH,
    WRITE_AROUND
}
