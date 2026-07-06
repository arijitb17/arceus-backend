package com.arijit.auditservice.dto;

import com.arijit.auditservice.entity.AuditLog;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String eventType,
        String actorEmail,
        String description,
        String metadata,
        Instant occurredAt,
        Instant receivedAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getEventType(), log.getActorEmail(), log.getDescription(),
                log.getMetadataJson(), log.getOccurredAt(), log.getReceivedAt()
        );
    }
}
