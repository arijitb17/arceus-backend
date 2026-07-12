package com.arijit.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per (channel, template code) - e.g. (EMAIL, "WELCOME"). Subject is
 * only meaningful for channels that have one (EMAIL); other channels can
 * leave it null and just use body.
 *
 * Placeholders in subject/body use {@code {{variableName}}} and are
 * substituted verbatim (no HTML escaping, no expression language) by
 * TemplateRenderer - deliberately simple until there's a real need for more.
 */
@Entity
@Table(name = "notification_templates", uniqueConstraints = @UniqueConstraint(columnNames = {"channel", "code"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    /** e.g. "WELCOME", "PASSWORD_RESET" - matches the "template" field callers send. */
    @Column(nullable = false, length = 100)
    private String code;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // Soft-disable instead of deleting: keeps notification history (which
    // references templateCode, not a foreign key to this row) meaningful,
    // and lets an admin turn a template off/on without losing its content.
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
