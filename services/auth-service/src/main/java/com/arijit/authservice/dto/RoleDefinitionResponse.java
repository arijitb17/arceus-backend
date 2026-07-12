package com.arijit.authservice.dto;

import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.RoleDefinition;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleDefinitionResponse(
        UUID id,
        Role roleKey,
        String name,
        String description,
        Set<PermissionResponse> permissions,
        Instant createdAt,
        Instant updatedAt
) {
    public static RoleDefinitionResponse from(RoleDefinition role) {
        return new RoleDefinitionResponse(
                role.getId(), role.getRoleKey(), role.getName(), role.getDescription(),
                role.getPermissions().stream().map(PermissionResponse::from).collect(Collectors.toSet()),
                role.getCreatedAt(), role.getUpdatedAt()
        );
    }
}
