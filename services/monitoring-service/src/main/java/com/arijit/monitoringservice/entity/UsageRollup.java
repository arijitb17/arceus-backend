package com.arijit.monitoringservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

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
