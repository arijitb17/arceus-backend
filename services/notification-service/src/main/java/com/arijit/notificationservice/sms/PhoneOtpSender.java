package com.arijit.notificationservice.sms;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;


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
            log.error("2Factor SMS send failed for phone={}: {}", LogMasker.phone(phoneNumber), response);
            throw new IllegalStateException("Failed to send SMS OTP");
        }

        log.debug("SMS OTP dispatched via 2Factor: phone={}", LogMasker.phone(phoneNumber));
        String sessionId = String.valueOf(response.get("Details"));
        redisTemplate.opsForValue().set(
                OtpRedisKeys.phoneSession(phoneNumber), sessionId, Duration.ofMinutes(expirationMinutes)
        );
    }
}
