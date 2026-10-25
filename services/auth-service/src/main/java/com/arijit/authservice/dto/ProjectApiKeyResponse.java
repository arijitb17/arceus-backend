package com.arijit.authservice.dto;

import com.arijit.authservice.entity.ApiKey;

import java.time.Instant;
import java.util.UUID;

public record ProjectApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        String keyType,
        String environment,
        String scopes,
        boolean revoked,
        Instant lastUsedAt,
        Instant createdAt
) {
    public static ProjectApiKeyResponse from(ApiKey apiKey) {
        return new ProjectApiKeyResponse(
                apiKey.getId(),
                apiKey.getName(),
                apiKey.getKeyPrefix(),
                apiKey.getKeyType() == null ? null : apiKey.getKeyType().name(),
                apiKey.getEnvironment(),
                apiKey.getScopes(),
                apiKey.isRevoked(),
                apiKey.getLastUsedAt(),
                apiKey.getCreatedAt()
        );
    }
}
