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
 * A project+environment scoped secret, per the architecture design's §8.
 * {@code encryptedValue} is envelope-encrypted (AES-GCM with a per-project
 * DEK, itself encrypted by a platform KEK supplied via
 * {@code arceus.organization.secrets.kek} - see SecretCryptoService) -
 * never stored or logged in plaintext. Never returned in plaintext through
 * any list/read API; only resolved server-side for the two consumers
 * described in the design doc (deploy-time env injection, module secret
 * resolution).
 * <p>
 * Versioned + soft-deleted (never hard-deleted) so a rotated or removed
 * secret still has an audit trail, the same retention posture API keys and
 * every other tenant resource in this design use.
 */
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

    /** e.g. "STRIPE_SECRET_KEY" - conventionally SCREAMING_SNAKE_CASE, matches how it's injected as an env var at deploy time. */
    @Column(nullable = false, length = 200)
    private String secretKey;

    /** Base64 ciphertext: AES-GCM(value) under this project+environment's DEK. Never exposed via any API response. */
    @Lob
    @Column(nullable = false)
    private String encryptedValue;

    /** The project+environment DEK's own id/version, itself encrypted under the platform KEK - lets the KEK rotate without re-encrypting every secret. */
    @Column(nullable = false, length = 100)
    private String encryptionKeyVersion;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 200)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant revokedAt;

    /**
     * Duplicated as a real column (not just "revokedAt != null") purely so
     * it can sit in the unique constraint above and let a revoked key name
     * be reused - Postgres unique constraints treat NULL revokedAt values
     * as distinct, which alone isn't restrictive enough once you also want
     * "at most one revoked row makes this true" semantics; a boolean is
     * simpler to reason about here than a partial index.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
