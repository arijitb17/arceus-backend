package com.arijit.notificationservice.dto;

import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;

import java.util.UUID;

public record TemplateResponse(
        UUID id,
        NotificationChannel channel,
        String code,
        String subject,
        String body,
        boolean active
) {
    public static TemplateResponse from(NotificationTemplate template) {
        return new TemplateResponse(
                template.getId(), template.getChannel(), template.getCode(),
                template.getSubject(), template.getBody(), template.isActive()
        );
    }
}
