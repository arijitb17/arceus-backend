package com.arijit.notificationservice.service;

import com.arijit.notificationservice.dto.*;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateManagementService {
    private final NotificationTemplateRepository templateRepository;

    public PagedResponse<TemplateResponse> list(NotificationChannel channel, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.ASC, "channel").and(Sort.by(Sort.Direction.ASC, "code")));

        Page<NotificationTemplate> result = (channel == null)
                ? templateRepository.findAll(pageable)
                : templateRepository.findByChannel(channel, pageable);

        return PagedResponse.of(result.map(TemplateResponse::from));
    }

    public TemplateResponse get(UUID id) {
        return TemplateResponse.from(findOrThrow(id));
    }

    @Transactional
    public TemplateResponse create(CreateTemplateRequest request) {
        if (templateRepository.existsByChannelAndCode(request.channel(), request.code())) {
            log.warn("Template create rejected: {} template '{}' already exists", request.channel(), request.code());
            throw new ApiException(
                    "A " + request.channel() + " template with code '" + request.code() + "' already exists",
                    HttpStatus.CONFLICT);
        }

        NotificationTemplate template = NotificationTemplate.builder()
                .channel(request.channel())
                .code(request.code())
                .subject(request.subject())
                .body(request.body())
                .active(true)
                .build();

        NotificationTemplate saved = templateRepository.save(template);
        log.info("Template created: id={} channel={} code={}", saved.getId(), saved.getChannel(), saved.getCode());
        return TemplateResponse.from(saved);
    }

    @Transactional
    public TemplateResponse update(UUID id, UpdateTemplateRequest request) {
        NotificationTemplate template = findOrThrow(id);
        template.setSubject(request.subject());
        template.setBody(request.body());
        log.info("Template updated: id={} channel={} code={}", id, template.getChannel(), template.getCode());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse updateStatus(UUID id, UpdateTemplateStatusRequest request) {
        NotificationTemplate template = findOrThrow(id);
        template.setActive(request.active());
        log.info("Template status changed: id={} channel={} code={} active={}", id, template.getChannel(), template.getCode(), request.active());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public void delete(UUID id) {
        NotificationTemplate template = findOrThrow(id);
        templateRepository.delete(template);
        log.info("Template deleted: id={} channel={} code={}", id, template.getChannel(), template.getCode());
    }

    private NotificationTemplate findOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> {
                    log.debug("Template lookup failed: id={}", id);
                    return new ApiException("No template found with id " + id, HttpStatus.NOT_FOUND);
                });
    }
}
