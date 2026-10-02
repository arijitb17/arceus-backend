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
 * Read-facing summary of a project's GitHub repo link, per the architecture
 * design's §6. The GitHub App installation itself (installationId, webhook
 * secret, webhook registration) is owned and stored by deployment-service,
 * since that's the service that actually receives webhooks and triggers
 * builds - this row exists purely so the Developer Portal can show
 * connection status without a cross-service call on every page load, kept
 * in sync via the {@code GithubRepoConnected}/{@code GithubRepoDisconnected}
 * events deployment-service publishes.
 */
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

    /** deployment-service's GithubInstallation id - this service never needs the installation token itself, only this reference. */
    @Column(nullable = false)
    private UUID installationId;

    @Column(nullable = false, length = 200)
    private String connectedBy;

    @Column(nullable = false, updatable = false)
    private Instant connectedAt;

    private Instant disconnectedAt;
}
