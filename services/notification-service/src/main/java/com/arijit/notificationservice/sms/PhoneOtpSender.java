package com.arijit.notificationservice.sms;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

// Triggers 2Factor to generate + send an OTP, then writes the session id to
// SHARED Redis under the key auth-service knows to read from during verify.
// This is the actual service boundary: auth-service never talks to 2Factor
// directly anymore, and this service never decides whether a code is right.
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneOtpSender {

    private final StringRedisTemplate redisTemplate;

    @Value("${sms.twofactor.api-key}")
    private String apiKey;

    @Value("${sms.twofactor.base-url}")
    private String baseUrl;

    @Value("${otp.expiration-minutes}")
    private long expirationMinutes;

    public void sendOtp(String phoneNumber) {
        RestClient client = RestClient.builder().baseUrl(baseUrl).build();

        Map<String, Object> response = client.get()
                .uri("/{apiKey}/SMS/{phone}/AUTOGEN", apiKey, phoneNumber)
                .retrieve()
                .body(Map.class);

        if (response == null || !"Success".equalsIgnoreCase(String.valueOf(response.get("Status")))) {
            log.error("2Factor SMS send failed for {}: {}", phoneNumber, response);
            throw new IllegalStateException("Failed to send SMS OTP");
        }

        String sessionId = String.valueOf(response.get("Details"));
        redisTemplate.opsForValue().set(
                OtpRedisKeys.phoneSession(phoneNumber), sessionId, Duration.ofMinutes(expirationMinutes)
        );
    }
}
