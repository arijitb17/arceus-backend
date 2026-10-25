package com.arijit.cacheservice.dto;

public record IncrementRequest(Long delta) {
    public long deltaOrDefault(long fallback) {
        return delta != null ? delta : fallback;
    }
}
