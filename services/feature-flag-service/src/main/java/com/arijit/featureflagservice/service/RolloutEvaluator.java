package com.arijit.featureflagservice.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deliberately dependency-free (no Spring wiring needed beyond {@code @Component}, no DB/cache
 * access) so it's trivially unit-testable and so FlagService/the gRPC layer can both call it the
 * same way.
 */
@Component
public class RolloutEvaluator {
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

    public int bucketOf(String flagKey, String bucketKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((flagKey + ":" + bucketKey).getBytes(StandardCharsets.UTF_8));
            long unsignedFirstFourBytes =
                    ((hash[0] & 0xFFL) << 24) | ((hash[1] & 0xFFL) << 16) | ((hash[2] & 0xFFL) << 8) | (hash[3] & 0xFFL);
            return (int) (unsignedFirstFourBytes % 100);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
