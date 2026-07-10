package com.arijit.featureflagservice.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Decides whether one evaluation falls inside a flag's rollout
 * percentage. Deliberately dependency-free (no Spring wiring needed
 * beyond {@code @Component}, no DB/cache access) so it's trivially
 * unit-testable and so FlagService/the gRPC layer can both call it the
 * same way.
 * <p>
 * Hashing {@code flagKey + ":" + bucketKey} (rather than just
 * {@code bucketKey}) means the same caller lands in a different bucket
 * for every different flag - two independently-configured 10% rollouts
 * don't end up selecting the exact same 10% of callers just because they
 * share a bucket key. When no bucket key is supplied, evaluation falls
 * back to a fresh random draw per call - not deterministic, but still a
 * correct percentage in aggregate, and reasonable for a caller that
 * genuinely doesn't have a stable identity to bucket on (e.g. a
 * fire-and-forget background task).
 */
@Component
public class RolloutEvaluator {

    /**
     * @param rolloutPercentage 0-100 inclusive
     * @param flagKey           the flag being evaluated, used to salt the hash per-flag
     * @param bucketKey         caller-supplied stable identity (user id, session id, ...), or null/blank
     */
    public boolean isIncluded(int rolloutPercentage, String flagKey, String bucketKey) {
        if (rolloutPercentage >= 100) {
            return true;
        }
        if (rolloutPercentage <= 0) {
            return false;
        }
        if (bucketKey == null || bucketKey.isBlank()) {
            return Math.random() * 100 < rolloutPercentage;
        }
        int bucket = bucketOf(flagKey, bucketKey);
        return bucket < rolloutPercentage;
    }

    /** Stable 0-99 bucket for (flagKey, bucketKey) - SHA-256 rather than String.hashCode() so bucket boundaries don't shift across JVM versions. */
    public int bucketOf(String flagKey, String bucketKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((flagKey + ":" + bucketKey).getBytes(StandardCharsets.UTF_8));
            long unsignedFirstFourBytes =
                    ((hash[0] & 0xFFL) << 24) | ((hash[1] & 0xFFL) << 16) | ((hash[2] & 0xFFL) << 8) | (hash[3] & 0xFFL);
            return (int) (unsignedFirstFourBytes % 100);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM - this is unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
