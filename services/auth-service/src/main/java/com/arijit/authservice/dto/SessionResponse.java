package com.arijit.authservice.dto;

import com.arijit.authservice.entity.RefreshToken;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        Instant createdAt,
        Instant expiresAt,
        boolean expired
) {
    public static SessionResponse from(RefreshToken token) {
        return new SessionResponse(
                token.getId(), token.getCreatedAt(), token.getExpiryDate(), token.isExpired()
        );
    }
}
