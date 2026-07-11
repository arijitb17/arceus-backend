package com.arijit.monitoringservice.entity;

/**
 * Status of one dependency component (database/cache/rabbit/gRPC) inside a
 * {@link ServiceHealthSnapshot}. {@code NOT_APPLICABLE} covers a service
 * that simply doesn't use that dependency (e.g. cache-service has no
 * RabbitMQ dependency) so it never drags an unrelated component into
 * DOWN/UNKNOWN in the UI.
 */
public enum ComponentStatus {
    UP,
    DOWN,
    UNKNOWN,
    NOT_APPLICABLE
}
