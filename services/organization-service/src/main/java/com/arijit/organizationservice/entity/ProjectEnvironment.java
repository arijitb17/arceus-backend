package com.arijit.organizationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

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

    @Column(length = 200)
    private String trackedBranch;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
