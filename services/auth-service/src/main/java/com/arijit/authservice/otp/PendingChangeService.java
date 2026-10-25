package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PendingChangeService {
    private final StringRedisTemplate redisTemplate;

    @Value("${otp.expiration-minutes}")
    private long expirationMinutes;

    public void storePendingEmail(UUID userId, String newEmail) {
        redisTemplate.opsForValue().set(OtpRedisKeys.pendingEmailChange(userId), newEmail, Duration.ofMinutes(expirationMinutes));
    }

    public Optional<String> consumePendingEmail(UUID userId) {
        return consume(OtpRedisKeys.pendingEmailChange(userId));
    }

    public void storePendingPhone(UUID userId, String newPhoneNumber) {
        redisTemplate.opsForValue().set(OtpRedisKeys.pendingPhoneChange(userId), newPhoneNumber, Duration.ofMinutes(expirationMinutes));
    }

    public Optional<String> consumePendingPhone(UUID userId) {
        return consume(OtpRedisKeys.pendingPhoneChange(userId));
    }

    private Optional<String> consume(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            redisTemplate.delete(key);
        }
        return Optional.ofNullable(value);
    }
}
