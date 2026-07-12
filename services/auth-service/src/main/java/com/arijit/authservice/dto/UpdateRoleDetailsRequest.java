package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleDetailsRequest(
        @NotBlank
        String name,

        String description
) {
}
