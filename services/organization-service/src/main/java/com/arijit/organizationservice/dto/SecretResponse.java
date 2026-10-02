package com.arijit.organizationservice.dto;

import com.arijit.organizationservice.entity.Secret;

import java.time.Instant;
import java.util.UUID;

/** Never carries the decrypted value - see Secret's javadoc for where plaintext is actually resolved. */
public record SecretResponse(UUID id, String secretKey, int version, String maskedValue, Instant createdAt) {
    public static SecretResponse from(Secret secret) {
        return new SecretResponse(secret.getId(), secret.getSecretKey(), secret.getVersion(), "••••••••", secret.getCreatedAt());
    }
}
