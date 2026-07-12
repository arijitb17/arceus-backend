package com.arijit.authservice.dto;

import com.arijit.authservice.entity.Permission;

import java.time.Instant;
import java.util.UUID;

public record PermissionResponse(
        UUID id,
        String code,
        String category,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(), permission.getCode(), permission.getCategory(),
                permission.getDescription(), permission.getCreatedAt(), permission.getUpdatedAt()
        );
    }
}
