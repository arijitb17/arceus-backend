package com.arijit.auditservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per domain event received from the events exchange. Every service
 * publishes events instead of writing audit rows directly (see roadmap) -
 * this is the only table in the platform that stores them.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_event_type", columnList = "eventType"),
        @Index(name = "idx_audit_actor_email", columnList = "actorEmail"),
        @Index(name = "idx_audit_occurred_at", columnList = "occurredAt")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** e.g. USER_REGISTERED, LOGIN_SUCCESSFUL, LOGIN_FAILED, PASSWORD_CHANGED... */
    @Column(nullable = false, length = 64)
    private String eventType;

    /** Best-effort - pulled from the event payload's "email" field when present, else null. */
    @Column(length = 255)
    private String actorEmail;

    /** Short human-readable summary, e.g. "User jane@x.com registered". */
    @Column(nullable = false, length = 500)
    private String description;

    /** Full event payload, as received, serialized as JSON - for anything the summary doesn't capture. */
    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    /** When the event actually happened, per the publisher. */
    @Column(nullable = false)
    private Instant occurredAt;

    /** When audit-service actually stored it - can lag occurredAt if the queue is backed up. */
    @Column(nullable = false)
    private Instant receivedAt;
}
