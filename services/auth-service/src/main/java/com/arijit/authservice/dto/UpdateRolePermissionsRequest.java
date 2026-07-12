package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

public record UpdateRolePermissionsRequest(
        @NotNull
        Set<UUID> permissionIds
) {
}
