package com.arijit.authservice.dto;

import com.arijit.authservice.entity.ApiKeyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** See ApiKeyService#createProjectKey - environment is a plain string matching organization-service's EnvironmentType (DEVELOPMENT/STAGING/PRODUCTION). */
public record CreateProjectApiKeyRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull ApiKeyType keyType,
        @NotBlank String environment,
        /** null/empty = every module the project currently has enabled - see ApiKey.scopes. */
        String scopes
) {}
