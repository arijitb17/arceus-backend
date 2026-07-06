package com.arijit.notificationservice.entity;

/**
 * EMAIL is the only channel actually wired to a sender today. The rest exist
 * now so the API contract (and callers) never have to change shape when
 * they're implemented - only NotificationDispatchService's internal switch
 * grows a case.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH,
    SLACK,
    DISCORD,
    WEBHOOK
}
