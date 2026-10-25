package com.arijit.gatewayservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "arceus.gateway")
public record GatewayProperties(
        Jwt jwt,
        List<String> publicPaths,
        RateLimit rateLimit,
        Cors cors
) {
    public record Jwt(String secret) {
    }

    public record RateLimit(int limit, int windowSeconds) {
    }

    public record Cors(List<String> allowedOrigins) {
        public List<String> originsOrEmpty() {
            return allowedOrigins == null ? List.of() : allowedOrigins;
        }
    }

    public boolean isPublic(String path) {
        if (publicPaths == null) return false;
        return publicPaths.stream().anyMatch(path::startsWith);
    }
}
