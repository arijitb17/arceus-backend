package com.arijit.featureflagservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RolloutEvaluatorTest {

    private final RolloutEvaluator evaluator = new RolloutEvaluator();

    @Test
    void zeroPercentAlwaysExcludes() {
        for (int i = 0; i < 50; i++) {
            assertFalse(evaluator.isIncluded(0, "flag-a", "user-" + i));
        }
    }

    @Test
    void hundredPercentAlwaysIncludes() {
        for (int i = 0; i < 50; i++) {
            assertTrue(evaluator.isIncluded(100, "flag-a", "user-" + i));
        }
    }

    @Test
    void sameBucketKeyIsDeterministicForTheSameFlag() {
        boolean first = evaluator.isIncluded(37, "flag-a", "stable-user");
        for (int i = 0; i < 20; i++) {
            assertEquals(first, evaluator.isIncluded(37, "flag-a", "stable-user"));
        }
    }

    @Test
    void differentFlagsBucketTheSameKeyIndependently() {
        // Not a strict guarantee for every possible key, but across many
        // flag names for one fixed bucket key, inclusion shouldn't be
        // identical for every single one - the flagKey salt is doing its job.
        String bucketKey = "user-42";
        boolean allSame = true;
        boolean first = evaluator.isIncluded(50, "flag-0", bucketKey);
        for (int i = 1; i < 30; i++) {
            if (evaluator.isIncluded(50, "flag-" + i, bucketKey) != first) {
                allSame = false;
                break;
            }
        }
        assertFalse(allSame, "Expected different flags to bucket the same key differently at least once");
    }

    @Test
    void bucketOfIsWithinValidRange() {
        for (int i = 0; i < 100; i++) {
            int bucket = evaluator.bucketOf("flag-a", "user-" + i);
            assertTrue(bucket >= 0 && bucket < 100);
        }
    }

    @Test
    void nullBucketKeyFallsBackToRandomDrawWithoutThrowing() {
        assertDoesNotThrow(() -> evaluator.isIncluded(50, "flag-a", null));
        assertDoesNotThrow(() -> evaluator.isIncluded(50, "flag-a", ""));
    }
}
