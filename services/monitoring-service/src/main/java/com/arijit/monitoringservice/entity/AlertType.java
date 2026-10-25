package com.arijit.monitoringservice.entity;

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
