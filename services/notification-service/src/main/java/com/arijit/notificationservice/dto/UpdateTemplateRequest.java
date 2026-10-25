package com.arijit.notificationservice.dto;

import jakarta.validation.constraints.NotBlank;

// Channel + code are immutable after creation (they're the lookup key NotificationDispatchService
// uses, and changing them out from under a template already in use would silently break every
// caller referencing the old code).
public record UpdateTemplateRequest(
        String subject,
        @NotBlank(message = "body is required") String body
) {}
