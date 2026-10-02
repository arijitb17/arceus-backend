package com.arijit.authservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A long-lived credential for programmatic/service-to-service access, as
 * an alternative to the short-lived JWT + refresh-token flow meant for
 * interactive logins.
 * <p>
 * Authentication itself only ever uses {@code keyHash} (SHA-256, one-way) -
 * see ApiKeyService#validateAndTouch. {@code encryptedKey} is a separate,
 * reversible copy that exists purely so the owner can retrieve the raw
 * key again later instead of only ever seeing it once at creation - see
 * ApiKeyCryptoService's javadoc for what that trade-off actually means
 * before assuming it's free.
 * <p>
 * {@code organizationId}/{@code projectId}/{@code environment}/{@code keyType}
 * are all nullable and default to null - a null {@code projectId} is a
 * <b>platform-level key</b> (today's only kind: an admin's own key for CI,
 * scripts, etc., scoped only to that admin's own user account, exactly as
 * before this field was added). A non-null {@code projectId} is a
 * <b>project-scoped key</b>, issued through ApiKeyService#createProjectKey
 * (architecture design §7) rather than the plain #create used by
 * ApiKeyController's existing self-service endpoints - see
 * ProjectApiKeyController for the developer-facing issuance path.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_key_hash", columnList = "keyHash", unique = true),
        @Index(name = "idx_api_key_user", columnList = "user_id"),
        @Index(name = "idx_api_key_project", columnList = "projectId,environment")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKey {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** User-supplied label, e.g. "CI pipeline" - purely for the owner's own bookkeeping. */
    @Column(nullable = false)
    private String name;

    /** SHA-256 hex digest of the raw key - this, not {@code encryptedKey}, is what authenticates a request. */
    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * The raw key, AES-256-GCM encrypted under {@code AUTH_API_KEY_ENCRYPTION_KEY}
     * - see ApiKeyCryptoService's javadoc. Decrypted back to plaintext only by
     * the reveal endpoints, only for the key's own owner.
     */
    @Column(columnDefinition = "TEXT")
    private String encryptedKey;

    /** First 8 characters of the raw key, shown in listings so an owner can tell keys apart at a glance without decrypting anything. */
    @Column(nullable = false, length = 8)
    private String keyPrefix;

    @Builder.Default
    private boolean revoked = false;

    private Instant lastUsedAt;

    @Column(updatable = false)
    private Instant createdAt;

    // ---------- Project-scoping (architecture design §7) - all null for a platform-level key ----------

    private UUID organizationId;

    private UUID projectId;

    /** "DEVELOPMENT" | "STAGING" | "PRODUCTION" - matches organization-service's EnvironmentType. Stored as a plain string here rather than an enum so auth-service never has to depend on organization-service's types. */
    @Column(length = 20)
    private String environment;

    /** PUBLIC (safe for client-side SDKs) or SECRET (server-side only) - null for a platform-level key, which has no such distinction. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ApiKeyType keyType;

    /**
     * Comma-separated module names this key may call (a subset of the
     * project's enabled modules), or null/blank to mean "every module the
     * project currently has enabled" - see ApiKeyService#createProjectKey.
     * A plain string rather than a join table: scopes are set once at
     * issuance and rarely change, and a key with dozens of scopes is not
     * an expected shape - if that changes, this is the seam to replace
     * with a proper child table.
     */
    @Column(length = 500)
    private String scopes;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
