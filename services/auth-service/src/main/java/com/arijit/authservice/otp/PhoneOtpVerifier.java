package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import com.arijit.authservice.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

// Only verification now - notification-service owns sending (and writes the
// 2Factor session id into the SAME Redis key this reads from, via
// OtpRedisKeys - the shared contract that keeps both sides in sync).
@Service
public class PhoneOtpVerifier {

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;

    @Value("${sms.twofactor.api-key}")
    private String apiKey;

    public PhoneOtpVerifier(StringRedisTemplate redisTemplate, @Value("${sms.twofactor.base-url}") String baseUrl) {
        this.redisTemplate = redisTemplate;
        // Built once, not per-call: a fresh RestClient per request was
        // harmless functionally but wasteful, and - more importantly - had
        // no timeout at all, so a stalled 2Factor API could hang this
        // thread indefinitely.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public boolean verify(String phoneNumber, String candidateOtp) {
        String sessionId = redisTemplate.opsForValue().get(OtpRedisKeys.phoneSession(phoneNumber));
        if (sessionId == null) {
            return false;
        }

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/{apiKey}/SMS/VERIFY/{sessionId}/{otp}", apiKey, sessionId, candidateOtp)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            // The provider being unreachable/slow isn't the same thing as
            // "wrong code" - returning false here would tell the user
            // their OTP was invalid when we actually just don't know.
            throw new ApiException("SMS verification provider is currently unavailable, please try again shortly",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        boolean success = response != null && "Success".equalsIgnoreCase(String.valueOf(response.get("Status")));
        if (success) {
            redisTemplate.delete(OtpRedisKeys.phoneSession(phoneNumber));
        }
        return success;
    }
}
