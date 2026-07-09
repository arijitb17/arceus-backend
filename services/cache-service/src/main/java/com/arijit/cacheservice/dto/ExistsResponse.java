package com.arijit.cacheservice.dto;

public record ExistsResponse(String namespace, String key, boolean exists) {
}
