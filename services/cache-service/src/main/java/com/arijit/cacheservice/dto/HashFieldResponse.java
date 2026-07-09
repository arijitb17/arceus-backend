package com.arijit.cacheservice.dto;

public record HashFieldResponse(String namespace, String key, String field, boolean found, Object value) {
}
