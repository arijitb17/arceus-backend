package com.arijit.notificationservice.service;

import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;
import com.arijit.notificationservice.entity.NotificationTemplate;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.notification.EmailService;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Every service in the platform should call this (via POST /notifications)
 * instead of implementing its own notification logic - see the roadmap's
 * "Every service should use Arceus Notification instead of implementing
 * notification logic".
 *
 * Flow: create a PENDING record -> attempt to render + send -> mark
 * SENT or FAILED. NotificationRetryScheduler picks FAILED records back up
 * later; this class doesn't loop/retry synchronously so the caller's HTTP
 * request never blocks on SMTP retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRecordRepository recordRepository;
    private final TemplateRenderer templateRenderer;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationRecord createAndSend(NotificationChannel channel, String templateCode, String to, Map<String, Object> variables) {
        NotificationTemplate template = templateRepository.findByChannelAndCode(channel, templateCode)
                .orElseThrow(() -> new ApiException(
                        "No " + channel + " template registered with code '" + templateCode + "'", HttpStatus.NOT_FOUND));

        Instant now = Instant.now();
        NotificationRecord record = NotificationRecord.builder()
                .channel(channel)
                .templateCode(templateCode)
                .recipient(to)
                .variablesJson(toJson(variables))
                .status(NotificationStatus.PENDING)
                .attempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        record = recordRepository.save(record);

        attemptSend(record, template, variables);
        return recordRepository.save(record);
    }

    /** Used by NotificationRetryScheduler - re-reads the template by code/channel since it isn't stored on the record. */
    @Transactional
    public void retry(NotificationRecord record) {
        NotificationTemplate template = templateRepository.findByChannelAndCode(record.getChannel(), record.getTemplateCode())
                .orElse(null);
        if (template == null) {
            record.setStatus(NotificationStatus.DEAD);
            record.setLastError("Template no longer exists");
            record.setUpdatedAt(Instant.now());
            recordRepository.save(record);
            return;
        }
        Map<String, Object> variables = fromJson(record.getVariablesJson());
        attemptSend(record, template, variables);
        recordRepository.save(record);
    }

    private void attemptSend(NotificationRecord record, NotificationTemplate template, Map<String, Object> variables) {
        record.setAttempts(record.getAttempts() + 1);
        record.setUpdatedAt(Instant.now());
        try {
            switch (record.getChannel()) {
                case EMAIL -> {
                    String subject = templateRenderer.render(template.getSubject(), variables);
                    String body = templateRenderer.render(template.getBody(), variables);
                    emailService.sendRenderedEmail(record.getRecipient(), subject, body);
                }
                default -> throw new UnsupportedOperationException(
                        record.getChannel() + " is not implemented yet - only EMAIL is wired to a real sender");
            }
            record.setStatus(NotificationStatus.SENT);
            record.setLastError(null);
            record.setSentAt(Instant.now());
        } catch (Exception e) {
            log.warn("Notification {} to {} via {} failed (attempt {}): {}",
                    record.getId(), record.getRecipient(), record.getChannel(), record.getAttempts(), e.getMessage());
            record.setStatus(NotificationStatus.FAILED);
            record.setLastError(e.getMessage());
        }
    }

    private String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? Map.of() : variables);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
