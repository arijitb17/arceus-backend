package com.arijit.schedulerservice.entity;

public enum JobTriggerType {
    IMMEDIATE,
    DELAY,
    CRON,
    FIXED_RATE,
    FIXED_DELAY;

    public boolean isRecurring() {
        return this == CRON || this == FIXED_RATE || this == FIXED_DELAY;
    }
}
