package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpRedisKeys;
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
public class PasswordResetTokenService {
    private final StringRedisTemplate redisTemplate;

    @Value("${otp.reset-token-expiration-minutes}")
    private long expirationMinutes;

    public String generateToken(String email) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(OtpRedisKeys.resetToken(token), email, Duration.ofMinutes(expirationMinutes));
        log.debug("Password reset token issued: ttlMinutes={}", expirationMinutes);
        return token;
    }

    public Optional<String> consumeToken(String token) {
        String key = OtpRedisKeys.resetToken(token);
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            redisTemplate.delete(key);
        } else {
            log.debug("Password reset token not found or expired");
        }
        return Optional.ofNullable(email);
    }
}
