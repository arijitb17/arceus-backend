package com.arijit.notificationservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per POST /notifications call - this is both "the thing waiting to
 * be sent" and, once it lands in SENT/DEAD, the delivery-history record
 * returned by GET /notifications.
 */
@Entity
@Table(name = "notification_records", indexes = {
        @Index(name = "idx_notification_status", columnList = "status"),
        @Index(name = "idx_notification_recipient", columnList = "recipient"),
        @Index(name = "idx_notification_created_at", columnList = "createdAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    @Column(nullable = false, length = 100)
    private String templateCode;

    @Column(nullable = false, length = 255)
    private String recipient;

    /** Variables the caller sent, serialized as JSON - kept for retries and history/debugging. */
    @Column(columnDefinition = "TEXT")
    private String variablesJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationStatus status;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant sentAt;
}
