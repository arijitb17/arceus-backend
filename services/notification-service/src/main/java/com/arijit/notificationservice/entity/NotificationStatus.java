package com.arijit.notificationservice.entity;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    RETRYING,
    DEAD,
    /** Channel was disabled via feature-flag-service at send time - see NotificationDispatchService / ChannelFlagGateService. Not a failure: nothing was attempted, and NotificationRetryScheduler leaves these alone. */
    SUPPRESSED
}
