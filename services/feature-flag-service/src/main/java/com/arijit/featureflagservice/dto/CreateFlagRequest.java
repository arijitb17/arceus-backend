package com.arijit.featureflagservice.dto;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.entity.TargetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

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
