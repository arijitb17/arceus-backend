package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.entity.FlagType;

/**
 * The subset of a {@link com.arijit.featureflagservice.entity.FeatureFlag} that evaluation actually
 * needs - deliberately not the full entity, so a cache hit never has to fabricate values
 * (createdAt, createdBy, ...) the entity requires but evaluation doesn't use. {@code valueJson} is
 * always the JSON-encoded effective value (see FlagValueCodec#toJsonString), regardless of {@code
 * type}, so this can round-trip through Jackson with no custom (de)serializer.
 */
public record FlagCacheEntry(
        String flagKey,
        FlagType type,
        FlagStatus status,
        int rolloutPercentage,
        String valueJson
) {
}
