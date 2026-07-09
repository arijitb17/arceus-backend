package com.arijit.cacheservice.dto;

/** ttlSeconds: -1 = no expiry, -2 = key does not exist. */
public record TtlResponse(String namespace, String key, long ttlSeconds) {
}
