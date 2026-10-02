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
 * One row per (project, environment, module) - the source of truth for
 * "is this module on for this project", per the architecture design's §4
 * module-provisioning workflow.
 * <p>
 * {@code status} moves through PROVISIONING/DISABLING to its terminal
 * ACTIVE/FAILED/DISABLED state within a single request:
 * {@code ModuleEnablementService.enable()}/{@code disable()} call the
 * target module's own service directly via {@code ModuleProvisioningClient}
 * and resolve the row before returning, rather than parking it at PENDING
 * to wait on an async result event from a separate orchestrator - see
 * ModuleProvisioningClient's javadoc for why.
 */
@Entity
@Table(name = "module_enablements", uniqueConstraints = {
        @UniqueConstraint(name = "uq_module_enablement", columnNames = {"projectId", "environment", "module"})
}, indexes = {
        @Index(name = "idx_module_enablement_project", columnList = "projectId,environment")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModuleEnablement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnvironmentType environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ModuleType module;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModuleStatus status;

    @Column(nullable = false, length = 200)
    private String requestedBy;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
