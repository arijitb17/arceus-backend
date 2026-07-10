package com.arijit.schedulerservice.dto;

import java.time.Instant;

/**
 * PUT /jobs/{id} body - only the fields a caller may still change once a
 * job exists. The schedule shape itself (triggerType and its params,
 * executionTarget) is intentionally NOT editable here: changing e.g.
 * DELAY into CRON on an existing row is really "cancel this, create a new
 * one" - JobService.update() rejects attempts to touch those fields via
 * a null-means-"leave alone" convention on every field below.
 */
public record UpdateJobRequest(
        String payload,
        Integer priority,
        Integer maxRetries,
        Long retryDelayMs,
        Double backoffMultiplier,
        Boolean exponentialBackoff,
        Instant expiresAt
) {
}
