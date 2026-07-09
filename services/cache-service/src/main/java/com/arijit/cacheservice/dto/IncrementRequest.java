package com.arijit.cacheservice.dto;

/** delta defaults to 1 (or -1 for the /decrement endpoint) when omitted. */
public record IncrementRequest(Long delta) {
    public long deltaOrDefault(long fallback) {
        return delta != null ? delta : fallback;
    }
}
