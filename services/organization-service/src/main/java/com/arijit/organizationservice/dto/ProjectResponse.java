package com.arijit.organizationservice.dto;

import com.arijit.organizationservice.entity.Project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        UUID organizationId,
        String name,
        String slug,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getOrganizationId(), project.getName(),
                project.getSlug(), project.getDescription(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
