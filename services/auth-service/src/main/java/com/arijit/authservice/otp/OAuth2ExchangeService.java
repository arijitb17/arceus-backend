package com.arijit.authservice.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ExchangeService {
    private static final String KEY_PREFIX = "oauth2:exchange:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.oauth2.exchange-code-expiration-seconds:60}")
    private long expirationSeconds;

    public String issueCode(UUID userId) {
        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + code, userId.toString(), Duration.ofSeconds(expirationSeconds));
        log.debug("OAuth2 exchange code issued: userId={} ttlSeconds={}", userId, expirationSeconds);
        return code;
    }

    public Optional<UUID> consumeCode(String code) {
        String key = KEY_PREFIX + code;
        String userId = redisTemplate.opsForValue().get(key);
        if (userId == null) {
            log.debug("OAuth2 exchange code not found or expired");
            return Optional.empty();
        }
        redisTemplate.delete(key);
        return Optional.of(UUID.fromString(userId));
    }
}
