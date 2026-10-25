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
@Table(name = "secrets", uniqueConstraints = {
        @UniqueConstraint(name = "uq_secret_project_env_key", columnNames = {"projectId", "environment", "secretKey", "revoked"})
}, indexes = {
        @Index(name = "idx_secret_project_env", columnList = "projectId,environment")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Secret {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnvironmentType environment;

    @Column(nullable = false, length = 200)
    private String secretKey;


    @Lob
    @Column(nullable = false)
    private String encryptedValue;

    @Column(nullable = false, length = 100)
    private String encryptionKeyVersion;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 200)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant revokedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
