package com.arijit.authservice.dto;

import com.arijit.authservice.entity.ApiKey;

import java.time.Instant;
import java.util.UUID;

public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        boolean revoked,
        Instant lastUsedAt,
        Instant createdAt
) {
    public static ApiKeyResponse from(ApiKey apiKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix() + "...",
                apiKey.isRevoked(),
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt()
        );
    }
}
