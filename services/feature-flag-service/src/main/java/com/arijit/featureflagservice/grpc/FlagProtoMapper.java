package com.arijit.featureflagservice.grpc;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagType;
import com.arijit.featureflagservice.entity.TargetType;
import com.arijit.featureflagservice.service.FlagValueCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Converts between the JPA {@link FeatureFlag} entity and {@code
 * com.arijit.arceus.grpc.featureflag.Flag} (the wire message - fully-qualified throughout since its
 * simple name collides with the entity).
 */
@Component
@RequiredArgsConstructor
public class FlagProtoMapper {
    private final FlagValueCodec valueCodec;

    public com.arijit.arceus.grpc.featureflag.Flag toProto(FeatureFlag flag) {
        return com.arijit.arceus.grpc.featureflag.Flag.newBuilder()
                .setId(flag.getId().toString())
                .setKey(flag.getFlagKey())
                .setName(flag.getName())
                .setDescription(flag.getDescription() != null ? flag.getDescription() : "")
                .setFlagType(flag.getType().name())
                .setEnvironment(flag.getEnvironment().name())
                .setTargetType(flag.getTargetType().name())
                .setValue(valueCodec.toJsonString(flag))
                .setStatus(flag.getStatus().name())
                .setRolloutPercentage(flag.getRolloutPercentage())
                .setActivateAtEpochMillis(toEpochMillis(flag.getActivateAt()))
                .setDeactivateAtEpochMillis(toEpochMillis(flag.getDeactivateAt()))
                .setCreatedBy(flag.getCreatedBy())
                .setUpdatedBy(flag.getUpdatedBy())
                .setCreatedAtEpochMillis(toEpochMillis(flag.getCreatedAt()))
                .setUpdatedAtEpochMillis(toEpochMillis(flag.getUpdatedAt()))
                .setVersion(flag.getVersion() != null ? flag.getVersion() : 0L)
                .build();
    }

    public FlagType parseType(String value) {
        return FlagType.valueOf(value.toUpperCase());
    }

    public Environment parseEnvironment(String value) {
        return (value == null || value.isBlank()) ? Environment.GLOBAL : Environment.valueOf(value.toUpperCase());
    }

    public TargetType parseTargetType(String value) {
        return (value == null || value.isBlank()) ? TargetType.ENVIRONMENT : TargetType.valueOf(value.toUpperCase());
    }

    public Instant parseInstant(long epochMillis) {
        return epochMillis <= 0 ? null : Instant.ofEpochMilli(epochMillis);
    }

    private long toEpochMillis(Instant instant) {
        return instant == null ? 0L : instant.toEpochMilli();
    }
}
