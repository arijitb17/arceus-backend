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
 * One row per (key, environment) pair - a flag with targetType=ENVIRONMENT
 * that's defined in DEVELOPMENT, STAGING, and PRODUCTION is three
 * independent rows sharing the same key, each with its own value/status/
 * rollout, created/updated/enabled/disabled independently. A flag with
 * targetType=GLOBAL has exactly one row, with environment fixed to
 * {@link Environment#GLOBAL}.
 * <p>
 * Only one of valueBoolean/valueLong/valueDouble/valueString/valueJson is
 * ever populated, chosen by {@link #type} - see FlagValueCodec, which is
 * the single place that knows how to move between "the caller-facing JSON
 * value" and "which column holds it".
 * <p>
 * {@link #version} backs both optimistic locking (a concurrent REST update
 * and a scheduler activation callback must not silently clobber each
 * other) and version numbering for {@link FlagVersion} snapshots.
 */
@Entity
@Table(name = "feature_flags",
        uniqueConstraints = @UniqueConstraint(name = "uk_flag_key_environment", columnNames = {"flagKey", "environment"}),
        indexes = {
                @Index(name = "idx_flag_key", columnList = "flagKey"),
                @Index(name = "idx_flag_key_env", columnList = "flagKey,environment")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Caller-facing identifier, e.g. "auth.registration-enabled" - stable across environments, unique per environment. */
    @Column(nullable = false, length = 200)
    private String flagKey;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private FlagType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TargetType targetType;

    private Boolean valueBoolean;

    /** Backs both INTEGER (range-validated to fit an int) and LONG. */
    private Long valueLong;

    private Double valueDouble;

    @Column(length = 4000)
    private String valueString;

    @Column(columnDefinition = "TEXT")
    private String valueJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagStatus status;

    /** 0-100. 100 (the default) means every evaluation is included once the flag is ENABLED. */
    @Builder.Default
    private int rolloutPercentage = 100;

    /** When set, FlagSchedulingService registers a scheduler-service job that flips status to ENABLED at this instant. */
    private Instant activateAt;

    /** When set, FlagSchedulingService registers a scheduler-service job that flips status to DISABLED at this instant. */
    private Instant deactivateAt;

    /** scheduler-service job id backing {@link #activateAt}, so it can be cancelled/rescheduled on update/delete. */
    @Column(length = 64)
    private String activateJobId;

    /** scheduler-service job id backing {@link #deactivateAt}. */
    @Column(length = 64)
    private String deactivateJobId;

    @Column(nullable = false, length = 200)
    private String createdBy;

    @Column(nullable = false, length = 200)
    private String updatedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Optimistic locking + version-history numbering - see FlagVersionService. */
    @Version
    private Long version;
}
