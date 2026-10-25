package com.arijit.cacheservice.dto;

public record TtlResponse(String namespace, String key, long ttlSeconds) {
}
