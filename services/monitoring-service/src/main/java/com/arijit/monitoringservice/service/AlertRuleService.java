package com.arijit.monitoringservice.service;

import com.arijit.monitoringservice.dto.AlertRuleRequest;
import com.arijit.monitoringservice.dto.AlertRuleResponse;
import com.arijit.monitoringservice.dto.PagedResponse;
import com.arijit.monitoringservice.entity.AlertRule;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.repository.AlertRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Alert Rule Management - the admin-only mutation half of the /alerts
 * surface (see SecurityConfig / AlertController's {@code @PreAuthorize}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleService {

    private final AlertRuleRepository alertRuleRepository;

    public PagedResponse<AlertRuleResponse> list(Pageable pageable) {
        return PagedResponse.of(alertRuleRepository.findAll(pageable).map(this::toDto));
    }

    public AlertRuleResponse get(UUID id) {
        return toDto(findOrThrow(id));
    }

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest request, String createdBy) {
        Instant now = Instant.now();
        AlertRule rule = AlertRule.builder()
                .name(request.name())
                .type(request.type())
                .serviceName(blankToNull(request.serviceName()))
                .thresholdValue(request.thresholdValue())
                .severity(request.severity())
                .enabled(request.enabled() == null || request.enabled())
                .description(request.description())
                .createdBy(createdBy)
                .createdAt(now)
                .updatedAt(now)
                .build();
        rule = alertRuleRepository.save(rule);
        log.info("Alert rule created: {} ({}) by {}", rule.getName(), rule.getId(), createdBy);
        return toDto(rule);
    }

    @Transactional
    public AlertRuleResponse update(UUID id, AlertRuleRequest request) {
        AlertRule rule = findOrThrow(id);
        rule.setName(request.name());
        rule.setType(request.type());
        rule.setServiceName(blankToNull(request.serviceName()));
        rule.setThresholdValue(request.thresholdValue());
        rule.setSeverity(request.severity());
        if (request.enabled() != null) {
            rule.setEnabled(request.enabled());
        }
        rule.setDescription(request.description());
        rule.setUpdatedAt(Instant.now());
        rule = alertRuleRepository.save(rule);
        log.info("Alert rule updated: {} ({})", rule.getName(), rule.getId());
        return toDto(rule);
    }

    @Transactional
    public void delete(UUID id) {
        AlertRule rule = findOrThrow(id);
        alertRuleRepository.delete(rule);
        log.info("Alert rule deleted: {} ({})", rule.getName(), id);
    }

    private AlertRule findOrThrow(UUID id) {
        return alertRuleRepository.findById(id)
                .orElseThrow(() -> new ApiException("Alert rule not found: " + id, HttpStatus.NOT_FOUND));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private AlertRuleResponse toDto(AlertRule rule) {
        return new AlertRuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getType().name(),
                rule.getServiceName(),
                rule.getThresholdValue(),
                rule.getSeverity().name(),
                rule.isEnabled(),
                rule.getDescription(),
                rule.getCreatedBy(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
