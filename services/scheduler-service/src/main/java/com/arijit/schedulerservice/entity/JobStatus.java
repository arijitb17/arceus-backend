package com.arijit.schedulerservice.entity;

public enum JobStatus {
    PENDING,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    RETRYING,
    CANCELLED,
    EXPIRED,
    PAUSED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
