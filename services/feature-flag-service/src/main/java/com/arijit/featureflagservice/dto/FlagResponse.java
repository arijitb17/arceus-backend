package com.arijit.featureflagservice.dto;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.entity.TargetType;

import java.time.Instant;
import java.util.UUID;

public record FlagResponse(
        UUID id,
        String key,
        String name,
        String description,
        FlagType type,
        Environment environment,
        TargetType targetType,
        Object value,
        FlagStatus status,
        int rolloutPercentage,
        Instant activateAt,
        Instant deactivateAt,
        String createdBy,
        String updatedBy,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public static FlagResponse from(FeatureFlag flag, Object decodedValue) {
        return new FlagResponse(
                flag.getId(),
                flag.getFlagKey(),
                flag.getName(),
                flag.getDescription(),
                flag.getType(),
                flag.getEnvironment(),
                flag.getTargetType(),
                decodedValue,
                flag.getStatus(),
                flag.getRolloutPercentage(),
                flag.getActivateAt(),
                flag.getDeactivateAt(),
                flag.getCreatedBy(),
                flag.getUpdatedBy(),
                flag.getCreatedAt(),
                flag.getUpdatedAt(),
                flag.getVersion() != null ? flag.getVersion() : 0L
        );
    }
}
