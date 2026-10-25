package com.arijit.authservice.dto;

import java.util.UUID;

/**
 * Returned exactly once, at creation time - {@code key} is never retrievable again after this
 * response.
 */
public record ProjectApiKeyCreatedResponse(
        UUID id,
        String name,
        String keyType,
        String environment,
        String key
) {}
