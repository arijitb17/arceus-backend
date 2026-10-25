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
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {
    private final NotificationTemplateRepository templateRepository;
    private final NotificationRecordRepository recordRepository;
    private final TemplateRenderer templateRenderer;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final com.arijit.notificationservice.featureflag.ChannelFlagGateService channelFlagGateService;

    @Transactional
    public NotificationRecord createAndSend(NotificationChannel channel, String templateCode, String to, Map<String, Object> variables) {
        NotificationTemplate template = templateRepository.findByChannelAndCodeAndActiveTrue(channel, templateCode)
                .orElseThrow(() -> new ApiException(
                        "No active " + channel + " template registered with code '" + templateCode + "'", HttpStatus.NOT_FOUND));

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

        if (!channelFlagGateService.isChannelEnabled(channel)) {
            log.info("Suppressing {} notification '{}' to {} - channel disabled via feature-flag-service", channel, templateCode, LogMasker.recipient(to));
            record.setStatus(NotificationStatus.SUPPRESSED);
            record.setLastError("Channel disabled via feature-flag-service");
            record.setUpdatedAt(Instant.now());
            return recordRepository.save(record);
        }

        record = recordRepository.save(record);

        attemptSend(record, template, variables);
        return recordRepository.save(record);
    }

    @Transactional
    public void retry(NotificationRecord record) {
        NotificationTemplate template = templateRepository.findByChannelAndCodeAndActiveTrue(record.getChannel(), record.getTemplateCode())
                .orElse(null);
        if (template == null) {
            record.setStatus(NotificationStatus.DEAD);
            record.setLastError("Template no longer exists or has been deactivated");
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
                    record.getId(), LogMasker.recipient(record.getRecipient()), record.getChannel(), record.getAttempts(), e.toString());
            log.debug("Notification {} failure stack trace", record.getId(), e);
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
            log.warn("Could not parse stored notification variables: {}", e.toString());
            return Map.of();
        }
    }
}
