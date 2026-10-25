package com.arijit.gatewayservice.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayPropertiesTest {

    private GatewayProperties props(List<String> publicPaths, List<String> origins) {
        return new GatewayProperties(new GatewayProperties.Jwt("s"), publicPaths,
                new GatewayProperties.RateLimit(10, 60), new GatewayProperties.Cors(origins));
    }

    @Test
    void isPublicMatchesByPrefix() {
        GatewayProperties p = props(List.of("/api/auth/login", "/health"), null);

        assertThat(p.isPublic("/api/auth/login")).isTrue();
        assertThat(p.isPublic("/api/auth/login/extra")).isTrue();
        assertThat(p.isPublic("/health")).isTrue();
        assertThat(p.isPublic("/api/auth/me")).isFalse();
    }

    @Test
    void nullPublicPathsMeansNothingIsPublic() {
        assertThat(props(null, null).isPublic("/health")).isFalse();
    }

    @Test
    void corsOriginsDefaultToEmpty() {
        assertThat(new GatewayProperties.Cors(null).originsOrEmpty()).isEmpty();
        assertThat(new GatewayProperties.Cors(List.of("https://a.io")).originsOrEmpty()).containsExactly("https://a.io");
    }
}
