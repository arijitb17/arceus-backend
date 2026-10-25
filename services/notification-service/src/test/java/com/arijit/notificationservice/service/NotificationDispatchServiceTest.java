package com.arijit.notificationservice.service;

import com.arijit.notificationservice.entity.*;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.featureflag.ChannelFlagGateService;
import com.arijit.notificationservice.notification.EmailService;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock NotificationTemplateRepository templateRepository;
    @Mock NotificationRecordRepository recordRepository;
    @Mock EmailService emailService;
    @Mock ChannelFlagGateService flagGate;

    private NotificationDispatchService service;
    private final NotificationTemplate welcome = NotificationTemplate.builder().id(UUID.randomUUID())
            .channel(NotificationChannel.EMAIL).code("WELCOME").subject("Hi {{name}}").body("Welcome, {{name}}!").active(true).build();

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(templateRepository, recordRepository, new TemplateRenderer(),
                emailService, new ObjectMapper(), flagGate);
        lenient().when(recordRepository.save(any(NotificationRecord.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void sendsRenderedEmailAndMarksSent() {
        when(templateRepository.findByChannelAndCodeAndActiveTrue(NotificationChannel.EMAIL, "WELCOME")).thenReturn(Optional.of(welcome));
        when(flagGate.isChannelEnabled(NotificationChannel.EMAIL)).thenReturn(true);

        NotificationRecord r = service.createAndSend(NotificationChannel.EMAIL, "WELCOME", "a@b.com", Map.of("name", "Ash"));

        verify(emailService).sendRenderedEmail("a@b.com", "Hi Ash", "Welcome, Ash!");
        assertThat(r.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(r.getAttempts()).isEqualTo(1);
        assertThat(r.getSentAt()).isNotNull();
        assertThat(r.getLastError()).isNull();
        assertThat(r.getVariablesJson()).isEqualTo("{\"name\":\"Ash\"}");
        verify(recordRepository, times(2)).save(any());
    }

    @Test
    void unknownOrInactiveTemplateIs404() {
        when(templateRepository.findByChannelAndCodeAndActiveTrue(any(), anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAndSend(NotificationChannel.EMAIL, "NOPE", "a@b.com", Map.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).contains("NOPE");
                });
        verifyNoInteractions(recordRepository, emailService);
    }

    @Test
    void disabledChannelIsSuppressedWithoutSending() {
        when(templateRepository.findByChannelAndCodeAndActiveTrue(any(), anyString())).thenReturn(Optional.of(welcome));
        when(flagGate.isChannelEnabled(NotificationChannel.EMAIL)).thenReturn(false);

        NotificationRecord r = service.createAndSend(NotificationChannel.EMAIL, "WELCOME", "a@b.com", Map.of("name", "x"));

        assertThat(r.getStatus()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(r.getAttempts()).isZero();
        assertThat(r.getLastError()).contains("feature-flag-service");
        verifyNoInteractions(emailService);
        verify(recordRepository, times(1)).save(any());
    }

    @Test
    void senderFailureMarksFailedAndKeepsError() {
        when(templateRepository.findByChannelAndCodeAndActiveTrue(any(), anyString())).thenReturn(Optional.of(welcome));
        when(flagGate.isChannelEnabled(any())).thenReturn(true);
        doThrow(new IllegalStateException("smtp down")).when(emailService).sendRenderedEmail(any(), any(), any());

        NotificationRecord r = service.createAndSend(NotificationChannel.EMAIL, "WELCOME", "a@b.com", Map.of());

        assertThat(r.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(r.getLastError()).isEqualTo("smtp down");
        assertThat(r.getAttempts()).isEqualTo(1);
        assertThat(r.getSentAt()).isNull();
    }

    @Test
    void unimplementedChannelFailsGracefully() {
        NotificationTemplate sms = NotificationTemplate.builder().channel(NotificationChannel.SMS).code("X").body("b").active(true).build();
        when(templateRepository.findByChannelAndCodeAndActiveTrue(NotificationChannel.SMS, "X")).thenReturn(Optional.of(sms));
        when(flagGate.isChannelEnabled(NotificationChannel.SMS)).thenReturn(true);

        NotificationRecord r = service.createAndSend(NotificationChannel.SMS, "X", "+911", null);

        assertThat(r.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(r.getLastError()).contains("not implemented");
        assertThat(r.getVariablesJson()).isEqualTo("{}");
    }

    @Test
    void retryResendsUsingStoredVariablesAndIncrementsAttempts() {
        NotificationRecord record = NotificationRecord.builder().id(UUID.randomUUID()).channel(NotificationChannel.EMAIL)
                .templateCode("WELCOME").recipient("a@b.com").variablesJson("{\"name\":\"Misty\"}")
                .status(NotificationStatus.FAILED).attempts(2).lastError("old").createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(templateRepository.findByChannelAndCodeAndActiveTrue(NotificationChannel.EMAIL, "WELCOME")).thenReturn(Optional.of(welcome));

        service.retry(record);

        verify(emailService).sendRenderedEmail("a@b.com", "Hi Misty", "Welcome, Misty!");
        assertThat(record.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(record.getAttempts()).isEqualTo(3);
        assertThat(record.getLastError()).isNull();
        verify(recordRepository).save(record);
    }

    @Test
    void retryWithMissingTemplateMarksDead() {
        NotificationRecord record = NotificationRecord.builder().channel(NotificationChannel.EMAIL).templateCode("GONE")
                .recipient("a@b.com").status(NotificationStatus.FAILED).attempts(1).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(templateRepository.findByChannelAndCodeAndActiveTrue(any(), eq("GONE"))).thenReturn(Optional.empty());

        service.retry(record);

        assertThat(record.getStatus()).isEqualTo(NotificationStatus.DEAD);
        assertThat(record.getLastError()).contains("no longer exists");
        verifyNoInteractions(emailService);
    }

    @Test
    void retryWithCorruptStoredVariablesStillSendsWithEmptyVariables() {
        NotificationRecord record = NotificationRecord.builder().channel(NotificationChannel.EMAIL).templateCode("WELCOME")
                .recipient("a@b.com").variablesJson("{broken").status(NotificationStatus.FAILED).attempts(1)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(templateRepository.findByChannelAndCodeAndActiveTrue(any(), anyString())).thenReturn(Optional.of(welcome));

        service.retry(record);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendRenderedEmail(eq("a@b.com"), anyString(), body.capture());
        assertThat(body.getValue()).isEqualTo("Welcome, {{name}}!");
    }
}
