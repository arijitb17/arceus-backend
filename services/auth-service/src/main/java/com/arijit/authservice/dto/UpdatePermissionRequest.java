package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePermissionRequest(
        @NotBlank
        String category,

        String description
) {
}
