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
@Table(name = "github_connections", uniqueConstraints = {
        @UniqueConstraint(name = "uq_github_connection_project", columnNames = {"projectId"})
}, indexes = {
        @Index(name = "idx_github_connection_project", columnList = "projectId")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GithubConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 300)
    private String repoFullName;

    @Column(nullable = false, length = 200)
    private String defaultBranch;

    @Column(nullable = false)
    private UUID installationId;

    @Column(nullable = false, length = 200)
    private String connectedBy;

    @Column(nullable = false, updatable = false)
    private Instant connectedAt;

    private Instant disconnectedAt;
}
