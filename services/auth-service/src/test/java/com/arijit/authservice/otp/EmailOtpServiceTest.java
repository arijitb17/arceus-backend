package com.arijit.authservice.otp;

import com.arijit.arceus.events.otp.OtpPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailOtpServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    private EmailOtpService service;

    @BeforeEach
    void setUp() {
        service = new EmailOtpService(redis);
        ReflectionTestUtils.setField(service, "expirationMinutes", 5L);
        lenient().when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void generatesSixDigitCodeAndStoresWithTtl() {
        String otp = service.generateAndStore("a@b.com", OtpPurpose.LOGIN_2FA);

        assertThat(otp).matches("\\d{6}");
        verify(ops).set("otp:email:LOGIN_2FA:a@b.com", otp, Duration.ofMinutes(5));
    }

    @Test
    void generatedCodesVary() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) codes.add(service.generateAndStore("a@b.com", OtpPurpose.LOGIN_2FA));
        assertThat(codes.size()).isGreaterThan(1);
    }

    @Test
    void verifyAcceptsMatchingCodeAndDeletesKey() {
        when(ops.get("otp:email:EMAIL_VERIFICATION:a@b.com")).thenReturn("123456");

        assertThat(service.verify("a@b.com", OtpPurpose.EMAIL_VERIFICATION, "123456")).isTrue();

        verify(redis).delete("otp:email:EMAIL_VERIFICATION:a@b.com");
    }

    @Test
    void verifyRejectsMismatchAndKeepsKey() {
        when(ops.get(any())).thenReturn("123456");

        assertThat(service.verify("a@b.com", OtpPurpose.EMAIL_VERIFICATION, "000000")).isFalse();

        verify(redis, never()).delete(anyString());
    }

    @Test
    void verifyRejectsMissingOrExpired() {
        when(ops.get(any())).thenReturn(null);

        assertThat(service.verify("a@b.com", OtpPurpose.EMAIL_VERIFICATION, "123456")).isFalse();
        assertThat(service.verify("a@b.com", OtpPurpose.EMAIL_VERIFICATION, null)).isFalse();
    }

    @Test
    void codeForOnePurposeDoesNotVerifyAnother() {
        when(ops.get("otp:email:FORGOT_PASSWORD:a@b.com")).thenReturn(null);

        assertThat(service.verify("a@b.com", OtpPurpose.FORGOT_PASSWORD, "123456")).isFalse();
    }
}
