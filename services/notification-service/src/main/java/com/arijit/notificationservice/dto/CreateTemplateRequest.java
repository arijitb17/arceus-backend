package com.arijit.notificationservice.dto;

import com.arijit.notificationservice.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTemplateRequest(
        @NotNull(message = "channel is required") NotificationChannel channel,
        @NotBlank(message = "code is required") String code,
        // Optional: only meaningful for channels that render a subject line (EMAIL).
        String subject,
        @NotBlank(message = "body is required") String body
) {}
