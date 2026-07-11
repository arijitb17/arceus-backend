package com.arijit.monitoringservice.entity;

/** Supported alert rule conditions - matches the spec's "Alert Rules -> Support" list exactly. */
public enum AlertType {
    SERVICE_DOWN,
    HIGH_CPU,
    HIGH_MEMORY,
    HIGH_ERROR_RATE,
    HIGH_RESPONSE_TIME,
    RABBITMQ_OFFLINE,
    REDIS_OFFLINE,
    DATABASE_OFFLINE
}
