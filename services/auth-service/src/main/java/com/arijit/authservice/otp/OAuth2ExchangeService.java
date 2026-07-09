package com.arijit.authservice.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges the OAuth2 redirect (a GET request the browser follows, which
 * means anything in the URL ends up in browser history, the server's
 * access logs, and any Referer header the frontend page sends afterwards)
 * and the actual token issuance.
 * <p>
 * Instead of putting the access/refresh tokens directly in the redirect
 * URL, {@code OAuth2LoginSuccessHandler} stores which user just logged in
 * behind a random, single-use, short-lived code, and redirects with only
 * that code. The frontend then POSTs the code to
 * {@code /api/auth/oauth2/exchange} to get the real tokens back in a
 * response body, which browsers don't log or keep in history.
 */
@Service
@RequiredArgsConstructor
public class OAuth2ExchangeService {

    private static final String KEY_PREFIX = "oauth2:exchange:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.oauth2.exchange-code-expiration-seconds:60}")
    private long expirationSeconds;

    /** Returns a fresh single-use code bound to this user id. */
    public String issueCode(UUID userId) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, userId.toString(), Duration.ofSeconds(expirationSeconds));
        return code;
    }

    /** Consumes the code (single-use: deletes it either way) and returns the user id it was issued for, if still valid. */
    public Optional<UUID> consumeCode(String code) {
        String key = KEY_PREFIX + code;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            return Optional.empty();
        }
        redisTemplate.delete(key);
        return Optional.of(UUID.fromString(userId));
    }
}
