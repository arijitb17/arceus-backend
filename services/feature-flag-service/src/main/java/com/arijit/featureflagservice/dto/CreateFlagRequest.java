package com.arijit.featureflagservice.dto;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.entity.TargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * POST /flags body. {@code value}'s shape depends on {@code type}: a JSON
 * boolean/number/string for BOOLEAN/INTEGER/LONG/DOUBLE/STRING, or any
 * JSON value (object, array, or scalar) for JSON - see FlagValueCodec,
 * which is the single place that validates a value actually matches the
 * declared type.
 * <p>
 * {@code targetType} defaults to ENVIRONMENT when omitted (the common
 * case - a per-environment flag); pass GLOBAL for a flag with one value
 * everywhere, in which case {@code environment} is ignored and stored as
 * {@link Environment#GLOBAL}. USER/ROLE/TENANT are accepted by the enum
 * but rejected by FlagValidationService today - not yet implemented.
 */
public record CreateFlagRequest(
        @NotBlank String key,
        @NotBlank String name,
        String description,
        @NotNull FlagType type,
        Environment environment,
        TargetType targetType,
        Object value,
        @Min(0) @Max(100) Integer rolloutPercentage,
        Instant activateAt,
        Instant deactivateAt,
        String actor
) {
}
