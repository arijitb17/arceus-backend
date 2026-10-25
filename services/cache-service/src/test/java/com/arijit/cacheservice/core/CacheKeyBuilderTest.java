package com.arijit.cacheservice.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyBuilderTest {

    private final CacheKeyBuilder builder = new CacheKeyBuilder();

    @Test
    void dataKeyJoinsNamespaceAndKey() {
        assertThat(builder.dataKey("session", "user:42")).isEqualTo("session:user:42");
    }

    @Test
    void metaKeyAppendsSuffixToDataKey() {
        assertThat(builder.metaKey("session", "u1")).isEqualTo("session:u1:__meta");
    }

    @Test
    void namespacePrefixIsGlob() {
        assertThat(builder.namespacePrefix("session")).isEqualTo("session:*");
    }

    @Test
    void differentNamespacesNeverCollide() {
        assertThat(builder.dataKey("a", "k")).isNotEqualTo(builder.dataKey("b", "k"));
    }
}
