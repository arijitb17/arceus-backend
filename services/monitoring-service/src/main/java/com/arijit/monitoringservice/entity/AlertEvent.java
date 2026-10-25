package com.arijit.monitoringservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_events",
        indexes = {
                @Index(name = "idx_alert_event_rule_service_state", columnList = "ruleId,serviceName,state"),
                @Index(name = "idx_alert_event_state_triggeredat", columnList = "state,triggeredAt")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID ruleId;

    @Column(nullable = false, length = 200)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AlertType type;

    @Column(nullable = false, length = 100)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertSeverity severity;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertState state;

    private double metricValue;

    @Column(nullable = false)
    private Instant triggeredAt;

    private Instant resolvedAt;
}
