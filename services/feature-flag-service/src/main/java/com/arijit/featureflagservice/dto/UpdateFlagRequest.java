package com.arijit.featureflagservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

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
