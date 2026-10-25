package com.arijit.cacheservice.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CacheDtoTest {

    private CacheRequest empty() {
        return new CacheRequest("ns", "k", "v", null, null, null, null, null, null, null);
    }

    @Test
    void cacheRequestDefaults() {
        CacheRequest r = empty();

        assertThat(r.typeOrDefault()).isEqualTo(CacheDataType.STRING);
        assertThat(r.compressOrDefault()).isFalse();
        assertThat(r.ttlPolicyOrDefault()).isEqualTo(TtlPolicy.NO_EXPIRY);
        assertThat(r.cachePolicyOrDefault()).isEqualTo(CachePolicy.CACHE_ASIDE);
    }

    @Test
    void cacheRequestExplicitValuesWin() {
        CacheRequest r = new CacheRequest("ns", "k", "v", CacheDataType.LIST, SerializationFormat.STRING, true,
                TtlPolicy.CUSTOM, 5L, Instant.now(), CachePolicy.WRITE_AROUND);

        assertThat(r.typeOrDefault()).isEqualTo(CacheDataType.LIST);
        assertThat(r.compressOrDefault()).isTrue();
        assertThat(r.ttlPolicyOrDefault()).isEqualTo(TtlPolicy.CUSTOM);
        assertThat(r.cachePolicyOrDefault()).isEqualTo(CachePolicy.WRITE_AROUND);
    }

    @Test
    void withNamespaceAndKeyOnlyChangesThose() {
        CacheRequest r = new CacheRequest("a", "b", "v", CacheDataType.SET, null, true, TtlPolicy.SLIDING, 9L, null, null);

        CacheRequest copy = r.withNamespaceAndKey("x", "y");

        assertThat(copy.namespace()).isEqualTo("x");
        assertThat(copy.key()).isEqualTo("y");
        assertThat(copy.value()).isEqualTo("v");
        assertThat(copy.type()).isEqualTo(CacheDataType.SET);
        assertThat(copy.ttlSeconds()).isEqualTo(9L);
        assertThat(r.namespace()).isEqualTo("a");
    }

    @Test
    void incrementAndHashFieldDefaults() {
        assertThat(new IncrementRequest(null).deltaOrDefault(1)).isEqualTo(1);
        assertThat(new IncrementRequest(-5L).deltaOrDefault(1)).isEqualTo(-5);
        assertThat(new HashFieldRequest("f", 1, null).serializationOrDefault(SerializationFormat.JSON))
                .isEqualTo(SerializationFormat.JSON);
        assertThat(new HashFieldRequest("f", 1, SerializationFormat.STRING).serializationOrDefault(SerializationFormat.JSON))
                .isEqualTo(SerializationFormat.STRING);
    }
}
