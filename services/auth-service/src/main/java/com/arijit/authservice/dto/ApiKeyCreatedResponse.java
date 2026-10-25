package com.arijit.authservice.dto;

import java.util.UUID;

public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String key
) {}
