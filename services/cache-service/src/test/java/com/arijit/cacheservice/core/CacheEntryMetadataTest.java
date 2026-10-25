package com.arijit.cacheservice.core;

import com.arijit.cacheservice.dto.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheEntryMetadataTest {

    private final Instant created = Instant.parse("2026-01-01T00:00:00Z");
    private final Instant updated = Instant.parse("2026-01-02T00:00:00Z");

    private CacheEntryMetadata sample() {
        return new CacheEntryMetadata(CacheDataType.HASH, SerializationFormat.BINARY, true,
                TtlPolicy.SLIDING, 90L, CachePolicy.WRITE_THROUGH, created, updated);
    }

    @Test
    void roundTripsThroughRedisHash() {
        Map<String, String> hash = sample().toRedisHash();

        assertThat(hash).containsEntry("type", "HASH").containsEntry("serialization", "BINARY")
                .containsEntry("compressed", "true").containsEntry("ttlPolicy", "SLIDING")
                .containsEntry("ttlSeconds", "90").containsEntry("cachePolicy", "WRITE_THROUGH");

        CacheEntryMetadata restored = CacheEntryMetadata.fromRedisHash(new HashMap<Object, Object>(hash));
        assertThat(restored).isEqualTo(sample());
    }

    @Test
    void nullOrEmptyHashMeansNoMetadata() {
        assertThat(CacheEntryMetadata.fromRedisHash(null)).isNull();
        assertThat(CacheEntryMetadata.fromRedisHash(new HashMap<>())).isNull();
    }

    @Test
    void corruptedHashFailsLoudly() {
        Map<Object, Object> hash = new HashMap<Object, Object>(sample().toRedisHash());
        hash.put("type", "NOT_A_TYPE");

        assertThatThrownBy(() -> CacheEntryMetadata.fromRedisHash(hash)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingFieldFailsLoudly() {
        Map<Object, Object> hash = new HashMap<Object, Object>(sample().toRedisHash());
        hash.remove("ttlSeconds");

        assertThatThrownBy(() -> CacheEntryMetadata.fromRedisHash(hash)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void withUpdatedNowKeepsEverythingButUpdatedAt() {
        CacheEntryMetadata bumped = sample().withUpdatedNow();

        assertThat(bumped.createdAt()).isEqualTo(created);
        assertThat(bumped.type()).isEqualTo(CacheDataType.HASH);
        assertThat(bumped.updatedAt()).isAfter(updated);
    }
}
