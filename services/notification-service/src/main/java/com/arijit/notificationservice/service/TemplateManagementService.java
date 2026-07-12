package com.arijit.notificationservice.service;

import com.arijit.notificationservice.dto.*;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Backs the admin-only /notifications/templates endpoints (answers gap #4
// from the backend-gaps doc: "no template-CRUD API"). Deliberately scoped
// to just content + active/inactive for now - no versioning, no preview
// endpoint, no locale variants. Those are real follow-on decisions (what
// counts as a "version"? does preview render against sample data or a real
// send?) rather than something to guess at here.
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

        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse update(UUID id, UpdateTemplateRequest request) {
        NotificationTemplate template = findOrThrow(id);
        template.setSubject(request.subject());
        template.setBody(request.body());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public TemplateResponse updateStatus(UUID id, UpdateTemplateStatusRequest request) {
        NotificationTemplate template = findOrThrow(id);
        template.setActive(request.active());
        return TemplateResponse.from(templateRepository.save(template));
    }

    @Transactional
    public void delete(UUID id) {
        NotificationTemplate template = findOrThrow(id);
        templateRepository.delete(template);
    }

    private NotificationTemplate findOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ApiException("No template found with id " + id, HttpStatus.NOT_FOUND));
    }
}
