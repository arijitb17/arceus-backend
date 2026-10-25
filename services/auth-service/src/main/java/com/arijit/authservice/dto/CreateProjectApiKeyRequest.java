package com.arijit.authservice.dto;

import com.arijit.authservice.entity.ApiKeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectApiKeyRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull ApiKeyType keyType,
        @NotBlank String environment,
        String scopes
) {}
