package com.arijit.notificationservice.notification;

import com.arijit.notificationservice.sms.PhoneOtpSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailAndSmsTest {

    @Mock JavaMailSender mailSender;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;

    @Test
    void otpEmailContainsCodeAndPurpose() {
        new EmailService(mailSender).sendOtpEmail("a@b.com", "123456", "logging in");

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(cap.capture());
        SimpleMailMessage m = cap.getValue();
        assertThat(m.getTo()).containsExactly("a@b.com");
        assertThat(m.getSubject()).isEqualTo("Your Arceus Platform verification code");
        assertThat(m.getText()).contains("logging in").contains("123456");
    }

    @Test
    void renderedEmailUsesGivenSubjectOrDefault() {
        EmailService service = new EmailService(mailSender);

        service.sendRenderedEmail("a@b.com", "Hello", "Body");
        service.sendRenderedEmail("a@b.com", null, "Body2");

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(cap.capture());
        assertThat(cap.getAllValues().get(0).getSubject()).isEqualTo("Hello");
        assertThat(cap.getAllValues().get(1).getSubject()).isEqualTo("Notification from Arceus Platform");
        assertThat(cap.getAllValues().get(1).getText()).isEqualTo("Body2");
    }

    @Test
    void mailFailurePropagatesSoCallersCanRetry() {
        doThrow(new org.springframework.mail.MailSendException("smtp")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> new EmailService(mailSender).sendRenderedEmail("a@b.com", "s", "b"))
                .isInstanceOf(org.springframework.mail.MailException.class);
    }

    @Test
    void smsOtpStoresProviderSessionOnSuccess() {
        RestClient.Builder builder = mock(RestClient.Builder.class, Answers.RETURNS_DEEP_STUBS);
        RestClient client = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
        when(builder.baseUrl(anyString()).build()).thenReturn(client);
        when(client.get().uri(anyString(), any(Object[].class)).retrieve().body(Map.class))
                .thenReturn(Map.of("Status", "Success", "Details", "session-77"));
        PhoneOtpSender sender = new PhoneOtpSender(redis);
        ReflectionTestUtils.setField(sender, "apiKey", "KEY");
        ReflectionTestUtils.setField(sender, "baseUrl", "http://sms.local");
        ReflectionTestUtils.setField(sender, "expirationMinutes", 5L);
        when(redis.opsForValue()).thenReturn(ops);

        try (var rc = mockStatic(RestClient.class)) {
            rc.when(RestClient::builder).thenReturn(builder);

            sender.sendOtp("+911234567890");
        }

        verify(ops).set("otp:phone:session:+911234567890", "session-77", Duration.ofMinutes(5));
    }

    @Test
    void smsOtpFailureThrowsAndStoresNothing() {
        RestClient.Builder builder = mock(RestClient.Builder.class, Answers.RETURNS_DEEP_STUBS);
        RestClient client = mock(RestClient.class, Answers.RETURNS_DEEP_STUBS);
        when(builder.baseUrl(anyString()).build()).thenReturn(client);
        when(client.get().uri(anyString(), any(Object[].class)).retrieve().body(Map.class))
                .thenReturn(Map.of("Status", "Error", "Details", "Invalid number"));
        PhoneOtpSender sender = new PhoneOtpSender(redis);
        ReflectionTestUtils.setField(sender, "apiKey", "KEY");
        ReflectionTestUtils.setField(sender, "baseUrl", "http://sms.local");
        ReflectionTestUtils.setField(sender, "expirationMinutes", 5L);

        try (var rc = mockStatic(RestClient.class)) {
            rc.when(RestClient::builder).thenReturn(builder);

            assertThatThrownBy(() -> sender.sendOtp("+911234567890"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Failed to send SMS OTP");
        }

        verifyNoInteractions(redis);
    }
}
