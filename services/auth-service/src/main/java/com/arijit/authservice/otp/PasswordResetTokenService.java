package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * After a forgot-password OTP is verified, we hand back a random one-time
 * token instead of making the client keep resubmitting the OTP. This maps
 * that token -> the email it's valid for, in Redis, with its own short TTL.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final StringRedisTemplate redisTemplate;

    @Value("${otp.reset-token-expiration-minutes}")
    private long expirationMinutes;

    public String generateToken(String email) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(OtpRedisKeys.resetToken(token), email, Duration.ofMinutes(expirationMinutes));
        return token;
    }

    /** Single-use: returns the email if valid, and deletes the token either way it's consumed. */
    public Optional<String> consumeToken(String token) {
        String key = OtpRedisKeys.resetToken(token);
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            redisTemplate.delete(key);
        }
        return Optional.ofNullable(email);
    }
}
