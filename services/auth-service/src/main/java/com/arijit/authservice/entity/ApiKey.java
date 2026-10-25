package com.arijit.authservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

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

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String keyHash;

    @Column(columnDefinition = "TEXT")
    private String encryptedKey;

    @Column(nullable = false, length = 8)
    private String keyPrefix;

    @Builder.Default
    private boolean revoked = false;

    private Instant lastUsedAt;

    @Column(updatable = false)
    private Instant createdAt;

    private UUID organizationId;

    private UUID projectId;

    @Column(length = 20)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ApiKeyType keyType;

    @Column(length = 500)
    private String scopes;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
