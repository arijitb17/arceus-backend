package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.entity.TargetType;
import com.arijit.featureflagservice.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlagValueCodecTest {
    private final FlagValueCodec codec = new FlagValueCodec(new ObjectMapper());

    private FeatureFlag.FeatureFlagBuilder baseFlag(FlagType type) {
        Instant now = Instant.now();
        return FeatureFlag.builder()
                .flagKey("test.flag")
                .name("Test Flag")
                .type(type)
                .environment(Environment.GLOBAL)
                .targetType(TargetType.GLOBAL)
                .status(FlagStatus.DISABLED)
                .rolloutPercentage(100)
                .createdBy("tester")
                .updatedBy("tester")
                .createdAt(now)
                .updatedAt(now);
    }

    @Test
    void roundTripsBoolean() {
        FeatureFlag flag = baseFlag(FlagType.BOOLEAN).build();
        codec.write(flag, FlagType.BOOLEAN, true);
        assertEquals(true, codec.read(flag));
        assertTrue(flag.getValueBoolean());
    }

    @Test
    void roundTripsInteger() {
        FeatureFlag flag = baseFlag(FlagType.INTEGER).build();
        codec.write(flag, FlagType.INTEGER, 42);
        assertEquals(42, codec.read(flag));
    }

    @Test
    void integerOutOfRangeIsRejected() {
        FeatureFlag flag = baseFlag(FlagType.INTEGER).build();
        assertThrows(ApiException.class, () -> codec.write(flag, FlagType.INTEGER, Long.MAX_VALUE));
    }

    @Test
    void roundTripsLong() {
        FeatureFlag flag = baseFlag(FlagType.LONG).build();
        codec.write(flag, FlagType.LONG, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, codec.read(flag));
    }

    @Test
    void roundTripsDouble() {
        FeatureFlag flag = baseFlag(FlagType.DOUBLE).build();
        codec.write(flag, FlagType.DOUBLE, 3.14);
        assertEquals(3.14, (Double) codec.read(flag), 0.0001);
    }

    @Test
    void roundTripsString() {
        FeatureFlag flag = baseFlag(FlagType.STRING).build();
        codec.write(flag, FlagType.STRING, "hello world");
        assertEquals("hello world", codec.read(flag));
    }

    @Test
    void roundTripsJsonObject() {
        FeatureFlag flag = baseFlag(FlagType.JSON).build();
        codec.write(flag, FlagType.JSON, Map.of("a", 1, "b", List.of("x", "y")));
        Object read = codec.read(flag);
        assertInstanceOf(Map.class, read);
    }

    @Test
    void nullJsonValueIsRejected() {
        FeatureFlag flag = baseFlag(FlagType.JSON).build();
        assertThrows(ApiException.class, () -> codec.write(flag, FlagType.JSON, null));
    }

    @Test
    void toJsonStringRoundTripsThroughFromJsonString() {
        FeatureFlag flag = baseFlag(FlagType.STRING).build();
        codec.write(flag, FlagType.STRING, "round-trip-me");
        String json = codec.toJsonString(flag);
        Object decoded = codec.fromJsonString(json, FlagType.STRING);
        assertEquals("round-trip-me", decoded);
    }
}
