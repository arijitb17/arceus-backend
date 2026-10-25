package com.arijit.notificationservice.messaging;

import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.arceus.events.otp.OtpChannel;
import com.arijit.arceus.events.otp.OtpNotificationMessage;
import com.arijit.arceus.events.otp.OtpPurpose;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.notification.EmailService;
import com.arijit.notificationservice.service.NotificationDispatchService;
import com.arijit.notificationservice.sms.PhoneOtpSender;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumersTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock EmailService emailService;
    @Mock PhoneOtpSender phoneOtpSender;
    @Mock IdempotencyGuard guard;
    @Mock NotificationDispatchService dispatch;

    @Nested
    class Idempotency {

        private IdempotencyGuard real() {
            IdempotencyGuard g = new IdempotencyGuard(redis);
            ReflectionTestUtils.setField(g, "ttlHours", 12L);
            return g;
        }

        @Test
        void blankIdsAreNeverProcessedOrMarked() {
            IdempotencyGuard g = real();

            assertThat(g.alreadyProcessed(null)).isFalse();
            assertThat(g.alreadyProcessed(" ")).isFalse();
            g.markProcessed(null);
            g.markProcessed("");

            verifyNoInteractions(redis);
        }

        @Test
        void alreadyProcessedReflectsRedisKey() {
            when(redis.hasKey("notification:processed:m1")).thenReturn(true, false, null);
            IdempotencyGuard g = real();

            assertThat(g.alreadyProcessed("m1")).isTrue();
            assertThat(g.alreadyProcessed("m1")).isFalse();
            assertThat(g.alreadyProcessed("m1")).isFalse();
        }

        @Test
        void markProcessedStoresWithTtl() {
            when(redis.opsForValue()).thenReturn(ops);

            real().markProcessed("m1");

            verify(ops).set("notification:processed:m1", "1", Duration.ofHours(12));
        }
    }

    @Nested
    class Otp {

        private final OtpConsumer consumer() {
            return new OtpConsumer(emailService, phoneOtpSender, guard);
        }

        @Test
        void emailOtpIsSentAndMarked() {
            OtpNotificationMessage m = OtpNotificationMessage.of(OtpChannel.EMAIL, "a@b.com", "123456", OtpPurpose.LOGIN_2FA, "Ash");

            consumer().handle(m);

            verify(emailService).sendOtpEmail("a@b.com", "123456", "logging in");
            verify(guard).markProcessed(m.messageId());
            verifyNoInteractions(phoneOtpSender);
        }

        @Test
        void smsOtpGoesThroughProvider() {
            OtpNotificationMessage m = OtpNotificationMessage.of(OtpChannel.SMS, "+911", null, OtpPurpose.PHONE_VERIFICATION, "Ash");

            consumer().handle(m);

            verify(phoneOtpSender).sendOtp("+911");
            verify(guard).markProcessed(m.messageId());
            verifyNoInteractions(emailService);
        }

        @Test
        void redeliveredMessageIsSkipped() {
            OtpNotificationMessage m = OtpNotificationMessage.of(OtpChannel.EMAIL, "a@b.com", "1", OtpPurpose.LOGIN_2FA, "Ash");
            when(guard.alreadyProcessed(m.messageId())).thenReturn(true);

            consumer().handle(m);

            verifyNoInteractions(emailService, phoneOtpSender);
            verify(guard, never()).markProcessed(anyString());
        }

        @Test
        void failureIsPropagatedForRetryAndNotMarkedProcessed() {
            OtpNotificationMessage m = OtpNotificationMessage.of(OtpChannel.EMAIL, "a@b.com", "1", OtpPurpose.LOGIN_2FA, "Ash");
            doThrow(new IllegalStateException("smtp")).when(emailService).sendOtpEmail(any(), any(), any());

            assertThatThrownBy(() -> consumer().handle(m)).isInstanceOf(IllegalStateException.class);

            verify(guard, never()).markProcessed(anyString());
        }

        @ParameterizedTest
        @CsvSource({
                "EMAIL_VERIFICATION,verifying your email",
                "PHONE_VERIFICATION,verifying your phone",
                "LOGIN_2FA,logging in",
                "FORGOT_PASSWORD,resetting your password",
                "EMAIL_CHANGE,confirming your new email address"})
        void purposeLabels(OtpPurpose purpose, String label) {
            consumer().handle(OtpNotificationMessage.of(OtpChannel.EMAIL, "a@b.com", "9", purpose, "n"));

            verify(emailService).sendOtpEmail("a@b.com", "9", label);
        }
    }

    @Nested
    class DomainEvents {

        private DomainEventConsumer consumer() {
            DomainEventConsumer c = new DomainEventConsumer(dispatch, guard);
            ReflectionTestUtils.setField(c, "deploymentAlertEmail", "alerts@x.io");
            return c;
        }

        private EventEnvelope event(String type, Map<String, Object> payload) {
            return EventEnvelope.of(type, Instant.now(), "svc", null, null, null, null, payload);
        }

        @Test
        void userRegisteredSendsWelcomeEmail() {
            EventEnvelope e = event("USER_REGISTERED", Map.of("email", "a@b.com", "name", "Ash"));

            consumer().handle(e);

            verify(dispatch).createAndSend(NotificationChannel.EMAIL, "WELCOME", "a@b.com", Map.of("name", "Ash"));
            verify(guard).markProcessed(e.eventId());
        }

        @ParameterizedTest
        @CsvSource({"DEPLOYMENT_COMPLETED,DEPLOYMENT_SUCCESS", "DEPLOYMENT_FAILED,DEPLOYMENT_FAILURE",
                "DEPLOYMENT_ROLLED_BACK,DEPLOYMENT_ROLLED_BACK", "DEPLOYMENT_CANCELLED,DEPLOYMENT_CANCELLED"})
        void deploymentEventsMapToTemplates(String eventType, String template) {
            consumer().handle(event(eventType, Map.of("deploymentId", "d1", "serviceName", "api",
                    "serviceVersion", "1.2", "environment", "prod", "status", "X")));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
            verify(dispatch).createAndSend(eq(NotificationChannel.EMAIL), eq(template), eq("alerts@x.io"), vars.capture());
            assertThat(vars.getValue()).containsEntry("deploymentId", "d1").containsEntry("serviceName", "api")
                    .containsEntry("createdBy", "unknown").containsEntry("error", "").containsEntry("restoredVersion", "");
        }

        @Test
        void unknownEventsAreIgnoredButStillMarked() {
            EventEnvelope e = event("SOMETHING_ELSE", Map.of());

            consumer().handle(e);

            verifyNoInteractions(dispatch);
            verify(guard).markProcessed(e.eventId());
        }

        @Test
        void duplicateEventSkipped() {
            EventEnvelope e = event("USER_REGISTERED", Map.of("email", "a@b.com", "name", "Ash"));
            when(guard.alreadyProcessed(e.eventId())).thenReturn(true);

            consumer().handle(e);

            verifyNoInteractions(dispatch);
        }

        @Test
        void dispatchFailurePropagatesWithoutMarking() {
            when(dispatch.createAndSend(any(), any(), any(), anyMap())).thenThrow(new IllegalStateException("db"));

            assertThatThrownBy(() -> consumer().handle(event("USER_REGISTERED", Map.of("email", "e", "name", "n"))))
                    .isInstanceOf(IllegalStateException.class);
            verify(guard, never()).markProcessed(anyString());
        }
    }
}
