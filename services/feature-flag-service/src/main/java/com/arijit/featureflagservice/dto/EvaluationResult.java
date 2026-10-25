package com.arijit.featureflagservice.dto;

import com.arijit.featureflagservice.entity.FlagType;

public record EvaluationResult(
        boolean found,
        boolean enabled,
        Object value,
        FlagType type,
        boolean fromCache
) {
    public static EvaluationResult notFound(Object defaultValue) {
        return new EvaluationResult(false, false, defaultValue, null, false);
    }
}
