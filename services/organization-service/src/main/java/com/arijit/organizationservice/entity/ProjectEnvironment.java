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
 * One row per (project, environment) - created automatically (all three)
 * the moment a Project is created, so a developer never has to explicitly
 * "create an environment". Everything else that's environment-scoped
 * (ModuleEnablement, Secret, API keys in auth-service) references the
 * project id + this enum directly rather than this row's id, since the
 * pair never needs to be looked up through here - this row mainly exists
 * as the anchor for environment-level metadata (branch tracking, in
 * particular).
 */
@Entity
@Table(name = "project_environments", uniqueConstraints = {
        @UniqueConstraint(name = "uq_project_environment", columnNames = {"projectId", "environment"})
}, indexes = {
        @Index(name = "idx_project_environment_project", columnList = "projectId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEnvironment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnvironmentType environment;

    /**
     * Which git branch is considered "live" for this environment -
     * informational project metadata only in this platform (no CI/CD
     * webhook currently reads or acts on it). Conventionally "main"/
     * "master" for PRODUCTION, "develop" for STAGING, null (unset) for
     * DEVELOPMENT until the developer sets one.
     */
    @Column(length = 200)
    private String trackedBranch;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
