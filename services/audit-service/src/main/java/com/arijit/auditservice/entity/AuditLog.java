package com.arijit.auditservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per domain event received from the events exchange. Every service
 * publishes events instead of writing audit rows directly (see roadmap) -
 * this is the only table in the platform that stores them.
 * <p>
 * This is the platform's system of record, not just a log: rows are never
 * updated or deleted (no {@code @Setter} on purpose - nothing in this
 * service has any business reason to mutate a row after it's created), and
 * {@code eventId} carries a unique constraint so a RabbitMQ redelivery of
 * the same event can never produce a second row - see
 * DomainEventAuditConsumer for how that's enforced.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_event_type", columnList = "eventType"),
        @Index(name = "idx_audit_actor_email", columnList = "actorEmail"),
        @Index(name = "idx_audit_occurred_at", columnList = "occurredAt"),
        @Index(name = "idx_audit_user_id", columnList = "userId"),
        @Index(name = "idx_audit_correlation_id", columnList = "correlationId")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_audit_event_id", columnNames = "eventId")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The publisher's idempotency key - the unique constraint on this column is what makes storage exactly-once. */
    @Column(nullable = false, length = 64, updatable = false)
    private String eventId;

    /** e.g. USER_REGISTERED, LOGIN_SUCCESSFUL, LOGIN_FAILED, PASSWORD_CHANGED... */
    @Column(nullable = false, length = 64, updatable = false)
    private String eventType;

    /** Which service published this event, e.g. "auth-service". */
    @Column(length = 64, updatable = false)
    private String sourceService;

    /** Ties this row back to the HTTP request that triggered it, across every service's logs. */
    @Column(length = 64, updatable = false)
    private String correlationId;

    /** The user this event is about/acting as, when known. */
    @Column(length = 64, updatable = false)
    private String userId;

    /** Best-effort - pulled from the event payload's "email" field when present, else null. */
    @Column(length = 255, updatable = false)
    private String actorEmail;

    /** Caller's IP, when the event was triggered by an HTTP request. */
    @Column(length = 64, updatable = false)
    private String ipAddress;

    /** Caller's User-Agent, when the event was triggered by an HTTP request. */
    @Column(length = 512, updatable = false)
    private String userAgent;

    /** Short human-readable summary, e.g. "User jane@x.com registered". */
    @Column(nullable = false, length = 500, updatable = false)
    private String description;

    /** Full event payload, as received, serialized as JSON - for anything the summary doesn't capture. */
    @Column(columnDefinition = "TEXT", updatable = false)
    private String metadataJson;

    /** When the event actually happened, per the publisher. */
    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    /** When audit-service actually stored it - can lag occurredAt if the queue is backed up. */
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;
}
