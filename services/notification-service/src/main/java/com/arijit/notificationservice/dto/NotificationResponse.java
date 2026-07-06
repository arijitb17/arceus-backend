package com.arijit.notificationservice.dto;

import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationChannel channel,
        String template,
        String to,
        NotificationStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant sentAt
) {
    public static NotificationResponse from(NotificationRecord r) {
        return new NotificationResponse(
                r.getId(), r.getChannel(), r.getTemplateCode(), r.getRecipient(),
                r.getStatus(), r.getAttempts(), r.getLastError(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getSentAt()
        );
    }
}
