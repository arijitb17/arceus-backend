package com.arijit.cacheservice.dto;

public record EvictionResponse(String scope, long keysEvicted) {
}
