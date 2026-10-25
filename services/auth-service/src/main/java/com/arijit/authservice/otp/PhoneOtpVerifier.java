package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpRedisKeys;
import com.arijit.authservice.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Service
public class PhoneOtpVerifier {
    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;

    @Value("${sms.twofactor.api-key}")
    private String apiKey;

    public PhoneOtpVerifier(StringRedisTemplate redisTemplate, @Value("${sms.twofactor.base-url}") String baseUrl) {
        this.redisTemplate = redisTemplate;
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
            log.debug("Phone OTP verify: no pending session for phone={}", LogMasker.phone(phoneNumber));
            return false;
        }

        Map<String, Object> response;
        try {
            response = restClient.get()
                    .uri("/{apiKey}/SMS/VERIFY/{sessionId}/{otp}", apiKey, sessionId, candidateOtp)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            log.error("2Factor SMS verification call failed for phone={}", LogMasker.phone(phoneNumber), e);
            throw new ApiException("SMS verification provider is currently unavailable, please try again shortly",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        boolean success = response != null && "Success".equalsIgnoreCase(String.valueOf(response.get("Status")));
        if (success) {
            redisTemplate.delete(OtpRedisKeys.phoneSession(phoneNumber));
        } else {
            log.debug("Phone OTP verify: code rejected for phone={}", LogMasker.phone(phoneNumber));
        }
        return success;
    }
}
