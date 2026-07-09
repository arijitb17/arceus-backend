package com.arijit.cacheservice.dto;

import jakarta.validation.constraints.Positive;

public record ExpireRequest(@Positive(message = "ttlSeconds must be positive") long ttlSeconds) {
}
