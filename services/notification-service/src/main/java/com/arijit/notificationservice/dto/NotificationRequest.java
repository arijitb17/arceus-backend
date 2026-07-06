package com.arijit.notificationservice.dto;

import com.arijit.notificationservice.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record NotificationRequest(

        @NotNull(message = "channel is required")
        NotificationChannel channel,

        @NotBlank(message = "template is required")
        String template,

        @NotBlank(message = "to is required")
        String to,

        Map<String, Object> variables
) {
}
