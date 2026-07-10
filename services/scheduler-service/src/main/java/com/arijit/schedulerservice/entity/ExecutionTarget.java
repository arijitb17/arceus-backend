package com.arijit.schedulerservice.entity;

/**
 * Where a due job's {@code payload} is delivered - scheduler-service never
 * executes a job's actual business logic itself (see the module Javadoc),
 * it only fires one of these two generic delivery mechanisms.
 */
public enum ExecutionTarget {
    /** POSTs {@code payload} as the JSON body to {@code callbackUrl}. 2xx = success, anything else = failure. */
    HTTP_CALLBACK,
    /** Publishes {@code payload} as the JSON message body to {@code targetExchange}/{@code targetRoutingKey}. Success = the broker accepted it. */
    MESSAGE_PUBLISH
}
