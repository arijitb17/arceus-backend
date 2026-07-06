package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

// Only verification now - notification-service owns sending (and writes the
// 2Factor session id into the SAME Redis key this reads from, via
// OtpRedisKeys - the shared contract that keeps both sides in sync).
@Service
@RequiredArgsConstructor
public class PhoneOtpVerifier {

    private final StringRedisTemplate redisTemplate;

    @Value("${sms.twofactor.api-key}")
    private String apiKey;

    @Value("${sms.twofactor.base-url}")
    private String baseUrl;

    public boolean verify(String phoneNumber, String candidateOtp) {
        String sessionId = redisTemplate.opsForValue().get(OtpRedisKeys.phoneSession(phoneNumber));
        if (sessionId == null) {
            return false;
        }

        RestClient client = RestClient.builder().baseUrl(baseUrl).build();
        Map<String, Object> response = client.get()
                .uri("/{apiKey}/SMS/VERIFY/{sessionId}/{otp}", apiKey, sessionId, candidateOtp)
                .retrieve()
                .body(Map.class);

        boolean success = response != null && "Success".equalsIgnoreCase(String.valueOf(response.get("Status")));
        if (success) {
            redisTemplate.delete(OtpRedisKeys.phoneSession(phoneNumber));
        }
        return success;
    }
}
