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
 * interactive logins. The raw key is shown to the user exactly once, at
 * creation time (see ApiKeyController) - only its SHA-256 hash is ever
 * persisted, the same reasoning as never storing a plaintext password.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_key_hash", columnList = "keyHash", unique = true),
        @Index(name = "idx_api_key_user", columnList = "user_id")
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

    /** SHA-256 hex digest of the raw key - never the raw key itself. */
    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    /** First 8 characters of the raw key, shown in listings so an owner can tell keys apart without ever seeing the full value again. */
    @Column(nullable = false, length = 8)
    private String keyPrefix;

    @Builder.Default
    private boolean revoked = false;

    private Instant lastUsedAt;

    @Column(updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
