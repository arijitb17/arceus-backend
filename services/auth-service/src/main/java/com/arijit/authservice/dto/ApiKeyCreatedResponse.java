package com.arijit.authservice.dto;

import java.util.UUID;

/** Returned exactly once, at creation time - {@code key} is never retrievable again after this response. */
public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String key
) {}
