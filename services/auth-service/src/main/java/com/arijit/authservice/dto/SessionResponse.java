package com.arijit.authservice.dto;

import com.arijit.authservice.entity.RefreshToken;

import java.time.Instant;
import java.util.UUID;

// A "session" here is a live refresh token - one per device/login, since
// AuthService.saveRefreshToken() creates exactly one row per successful
// login/OAuth exchange and logout()/refresh-rotation deletes it. We never
// expose the token hash itself, just enough to let someone recognize and
// revoke a session ("issued 2 days ago, expires in 5").
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
