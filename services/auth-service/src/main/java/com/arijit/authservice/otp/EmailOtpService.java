package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpPurpose;
import com.arijit.arceus.events.otp.OtpRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailOtpService {
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${otp.expiration-minutes}")
    private long expirationMinutes;

    public String generateAndStore(String email, OtpPurpose purpose) {
        String otp = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(OtpRedisKeys.emailOtp(email, purpose), otp, Duration.ofMinutes(expirationMinutes));
        log.debug("Email OTP stored: purpose={} email={} ttlMinutes={}", purpose, LogMasker.email(email), expirationMinutes);
        return otp;
    }

    public boolean verify(String email, OtpPurpose purpose, String candidate) {
        String key = OtpRedisKeys.emailOtp(email, purpose);
        String stored = redisTemplate.opsForValue().get(key);

        if (stored == null || !stored.equals(candidate)) {
            log.debug("Email OTP rejected: purpose={} email={} reason={}", purpose, LogMasker.email(email),
                    stored == null ? "missing_or_expired" : "mismatch");
            return false;
        }

        redisTemplate.delete(key);
        return true;
    }
}
