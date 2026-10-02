package com.arijit.organizationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The billing/ownership boundary a developer operates under. Everything a
 * developer creates in the Developer Portal - projects, and therefore
 * everything a project owns (§3 of the architecture design: keys, secrets,
 * modules, deployments, usage, logs) - hangs off exactly one Organization.
 * <p>
 * {@code ownerUserId} references a user id issued by auth-service. This
 * service deliberately does not duplicate any user profile fields (name,
 * email) - it only stores the id and resolves display info via auth-service
 * when needed, the same way deployment-service stores `createdBy` as a
 * plain string rather than owning a User table of its own.
 */
@Entity
@Table(name = "organizations", indexes = {
        @Index(name = "idx_organization_owner", columnList = "ownerUserId"),
        @Index(name = "idx_organization_slug", columnList = "slug", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    /** URL/API-safe identifier, e.g. "acme-inc" - unique platform-wide, used in SDK config and CLI. */
    @Column(nullable = false, length = 150, unique = true)
    private String slug;

    @Column(nullable = false, length = 200)
    private String ownerUserId;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Soft delete - preserves history for projects/deployments/audit that reference this org. */
    private Instant deletedAt;

    @Version
    private Long version;
}
