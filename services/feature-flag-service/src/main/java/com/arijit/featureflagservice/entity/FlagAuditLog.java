package com.arijit.featureflagservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * "Who changed what, when" for GET /flags/history - kept locally so that endpoint stays fast and
 * self-contained, in addition to (not instead of) publishing the same change onto the platform's
 * domain event bus (see FlagEventPublisher) for audit-service's own independent system of record.
 * flagId is nullable because a DELETE's audit row must still be queryable after the flag row itself
 * is gone.
 */
@Entity
@Table(name = "feature_flag_audit_log", indexes = {
        @Index(name = "idx_flag_audit_flag_key", columnList = "flagKey"),
        @Index(name = "idx_flag_audit_timestamp", columnList = "timestamp")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlagAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID flagId;

    @Column(nullable = false, length = 200)
    private String flagKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Column(nullable = false, length = 20)
    private String action;

    @Column(nullable = false, length = 200)
    private String actor;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(length = 64)
    private String correlationId;

    @Column(nullable = false)
    private Instant timestamp;
}
