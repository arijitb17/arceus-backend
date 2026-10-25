package com.arijit.authservice.otp;

import com.arijit.authservice.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhoneOtpVerifierTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) RestClient restClient;

    private PhoneOtpVerifier verifier;
    private static final String PHONE = "+919876543210";

    @BeforeEach
    void setUp() {
        verifier = new PhoneOtpVerifier(redis, "http://localhost:0");
        ReflectionTestUtils.setField(verifier, "restClient", restClient);
        ReflectionTestUtils.setField(verifier, "apiKey", "KEY");
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @SuppressWarnings("unchecked")
    private void stubProviderResponse(Map<String, Object> body) {
        when(restClient.get()
                .uri(eq("/{apiKey}/SMS/VERIFY/{sessionId}/{otp}"), eq("KEY"), eq("sess-1"), eq("123456"))
                .retrieve()
                .body(Map.class)).thenReturn(body);
    }

    @Test
    void returnsFalseWhenNoPendingSession() {
        when(ops.get("otp:phone:session:" + PHONE)).thenReturn(null);

        assertThat(verifier.verify(PHONE, "123456")).isFalse();

        verifyNoInteractions(restClient);
    }

    @Test
    void successDeletesSession() {
        when(ops.get("otp:phone:session:" + PHONE)).thenReturn("sess-1");
        stubProviderResponse(Map.of("Status", "Success"));

        assertThat(verifier.verify(PHONE, "123456")).isTrue();

        verify(redis).delete("otp:phone:session:" + PHONE);
    }

    @Test
    void statusMatchIsCaseInsensitive() {
        when(ops.get(any())).thenReturn("sess-1");
        stubProviderResponse(Map.of("Status", "SUCCESS"));

        assertThat(verifier.verify(PHONE, "123456")).isTrue();
    }

    @Test
    void failureKeepsSessionSoUserCanRetry() {
        when(ops.get(any())).thenReturn("sess-1");
        stubProviderResponse(Map.of("Status", "Error", "Details", "OTP Mismatch"));

        assertThat(verifier.verify(PHONE, "123456")).isFalse();

        verify(redis, never()).delete(any(String.class));
    }

    @Test
    void nullBodyIsTreatedAsFailure() {
        when(ops.get(any())).thenReturn("sess-1");
        stubProviderResponse(null);

        assertThat(verifier.verify(PHONE, "123456")).isFalse();
    }

    @Test
    void providerOutageBecomesServiceUnavailable() {
        when(ops.get(any())).thenReturn("sess-1");
        when(restClient.get()).thenThrow(new RestClientException("boom"));

        assertThatThrownBy(() -> verifier.verify(PHONE, "123456"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }
}
