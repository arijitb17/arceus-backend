package com.arijit.organizationservice.dto;

import com.arijit.organizationservice.entity.Organization;
import com.arijit.organizationservice.entity.OrgRole;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String ownerUserId,
        OrgRole callerRole,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationResponse from(Organization org, OrgRole callerRole) {
        return new OrganizationResponse(org.getId(), org.getName(), org.getSlug(), org.getDescription(),
                org.getOwnerUserId(), callerRole, org.getCreatedAt(), org.getUpdatedAt());
    }
}
