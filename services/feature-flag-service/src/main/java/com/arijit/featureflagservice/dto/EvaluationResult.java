package com.arijit.featureflagservice.dto;

import com.arijit.featureflagservice.entity.FlagType;

/**
 * Result of resolving a flag's effective value for a caller - the shape
 * both the (future) REST evaluate convenience endpoint and
 * SchedulerGrpcServiceImpl's EvaluateFlag RPC return. Not a JPA entity or
 * a raw {@link com.arijit.featureflagservice.entity.FeatureFlag} because
 * "found=false" (flag doesn't exist) and "enabled=false, using the
 * caller's default" are both valid, common outcomes that don't map onto
 * a flag row at all.
 */
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
