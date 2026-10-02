package com.arijit.organizationservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateSecretRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "must be SCREAMING_SNAKE_CASE") String secretKey,
        @NotBlank String value
) {}
