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
 * The isolated tenant unit (architecture design §3). Everything module
 * enablement, secrets, API keys, deployments, usage, and logs scope to is
 * keyed by this id + an {@link EnvironmentType}. This service owns the
 * project's identity and configuration; it does not own the actual
 * resources modules provision (those live in each owning service, keyed
 * back to this id - see ModuleEnablement's javadoc).
 */
@Entity
@Table(name = "projects", indexes = {
        @Index(name = "idx_project_organization", columnList = "organizationId"),
        @Index(name = "idx_project_slug", columnList = "organizationId,slug", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 150)
    private String name;

    /** Unique within the organization (not platform-wide, unlike Organization.slug). Used in generated SDK config. */
    @Column(nullable = false, length = 150)
    private String slug;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 200)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    @Version
    private Long version;
}
