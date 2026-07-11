package com.arijit.monitoringservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A user-manageable (POST/PUT/DELETE /alerts) or platform-seeded (see
 * DefaultAlertRuleSeeder) condition that {@link
 * com.arijit.monitoringservice.service.AlertEvaluationService} checks
 * against the latest health/metric snapshot of every matching service on
 * every polling cycle. {@code serviceName == null} means the rule applies
 * platform-wide, to every monitored service independently (e.g. the default
 * SERVICE_DOWN rule); a non-null value scopes it to one service only.
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertType type;

    /** null = platform-wide (evaluated independently against every monitored service). */
    @Column(length = 100)
    private String serviceName;

    /**
     * Meaning depends on {@link #type}: a percentage for HIGH_CPU/HIGH_MEMORY/
     * HIGH_ERROR_RATE, milliseconds for HIGH_RESPONSE_TIME, unused (may be 0)
     * for the boolean-condition types (SERVICE_DOWN, *_OFFLINE).
     */
    private double thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 200)
    private String createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;
}
