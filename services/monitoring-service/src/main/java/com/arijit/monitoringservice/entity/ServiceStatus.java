package com.arijit.monitoringservice.entity;

/** Overall status of one monitored service, as last observed by ServicePollingService. */
public enum ServiceStatus {
    UP,
    DEGRADED,
    DOWN,
    UNKNOWN
}
