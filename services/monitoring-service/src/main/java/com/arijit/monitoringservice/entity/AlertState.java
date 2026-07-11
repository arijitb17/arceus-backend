package com.arijit.monitoringservice.entity;

/** Lifecycle of one AlertEvent - a rule firing creates an ACTIVE event; the condition clearing resolves it. */
public enum AlertState {
    ACTIVE,
    RESOLVED
}
