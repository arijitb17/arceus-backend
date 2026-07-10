package com.arijit.featureflagservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

/**
 * PUT /flags/{key} body - only a flag's mutable fields (see FeatureFlag's
 * javadoc: key/type/targetType are fixed at creation, environment is
 * fixed by which row this is). Every field is optional/nullable; only
 * non-null fields are applied - same partial-update convention as
 * scheduler-service's UpdateJobRequest. Passing {@code activateAt}/
 * {@code deactivateAt} as present-but-null is not distinguishable from
 * "leave as-is" here; use enable/disable directly to clear a schedule.
 */
public record UpdateFlagRequest(
        String name,
        String description,
        Object value,
        @Min(0) @Max(100) Integer rolloutPercentage,
        Instant activateAt,
        Instant deactivateAt,
        String actor
) {
}
