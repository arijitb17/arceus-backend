package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreatePermissionRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9_-]+:[a-z0-9_-]+$", message = "code must look like 'resource:action', e.g. 'users:write'")
        String code,

        @NotBlank
        String category,

        String description
) {
}
