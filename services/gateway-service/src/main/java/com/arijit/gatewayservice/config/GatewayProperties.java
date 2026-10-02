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

    /**
     * The Gateway is the one public REST surface browsers actually talk to
     * (every downstream service is only reachable through it), so CORS has
     * to be handled here - the per-service CORS config in auth-service's
     * SecurityConfig only applies to requests that reach auth-service
     * directly (e.g. the OAuth2 redirect flow), not requests proxied
     * through the Gateway.
     */
    public record Cors(List<String> allowedOrigins) {
        public List<String> originsOrEmpty() {
            return allowedOrigins == null ? List.of() : allowedOrigins;
        }
    }

    /**
     * True if the given request path (e.g. "/api/auth/login") should be
     * allowed through without a valid access token.
     */
    public boolean isPublic(String path) {
        if (publicPaths == null) return false;
        return publicPaths.stream().anyMatch(path::startsWith);
    }
}
