package com.arijit.monitoringservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One row per (project, environment, route, day) - a daily rollup, not a
 * row per request. gateway-service publishes one lightweight event per
 * SDK/API-key request (see its UsageEventPublisher); storing each of
 * those as its own row would make this table the platform's single
 * highest-write-volume table for no benefit a developer's Usage tab
 * actually needs - "how many calls did Cache get today" doesn't need
 * per-request granularity, so UsageRollupConsumer upserts into this table
 * (increment requestCount for today's row) rather than inserting a new
 * row per event.
 * <p>
 * {@code route} is the Gateway's own Route enum name (e.g. "CACHE",
 * "AUTH") - a label for which downstream surface was called, not
 * necessarily identical to organization-service's ModuleType naming
 * (AUTH vs AUTHENTICATION) - this table is about traffic shape, not a
 * source of truth for module state.
 */
@Entity
@Table(name = "usage_rollups", uniqueConstraints = {
        @UniqueConstraint(name = "uq_usage_rollup_bucket", columnNames = {"projectId", "environment", "route", "day"})
}, indexes = {
        @Index(name = "idx_usage_rollup_project", columnList = "projectId,environment,day")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageRollup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 20)
    private String environment;

    @Column(nullable = false, length = 50)
    private String route;

    @Column(nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    @Builder.Default
    private long requestCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private long errorCount = 0;

    @Version
    private Long version;
}
