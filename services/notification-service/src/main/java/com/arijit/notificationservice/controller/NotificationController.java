package com.arijit.notificationservice.controller;

import com.arijit.notificationservice.dto.NotificationRequest;
import com.arijit.notificationservice.dto.NotificationResponse;
import com.arijit.notificationservice.dto.PagedResponse;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationRecord;
import com.arijit.notificationservice.entity.NotificationStatus;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.service.NotificationDispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The one API every other service should call instead of implementing its
 * own email/SMS/push logic - see roadmap Step 2. Admin-only: this can send
 * arbitrary messages to arbitrary recipients and exposes delivery history
 * (recipient emails/phone numbers), neither of which any authenticated
 * end user should be able to reach.
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Send templated notifications and inspect delivery history")
@PreAuthorize("hasRole('ADMIN')")
public class NotificationController {

    private final NotificationDispatchService dispatchService;
    private final NotificationRecordRepository recordRepository;

    @PostMapping("/notifications")
    @Operation(summary = "Send a notification via a channel + template code, e.g. {channel: EMAIL, template: WELCOME, to, variables}")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody NotificationRequest request) {
        NotificationRecord record = dispatchService.createAndSend(
                request.channel(), request.template(), request.to(), request.variables()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(NotificationResponse.from(record));
    }

    @GetMapping("/notifications/{id}")
    @Operation(summary = "Delivery status for a single notification")
    public ResponseEntity<NotificationResponse> getById(@PathVariable UUID id) {
        NotificationRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new ApiException("No notification with id " + id, HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(NotificationResponse.from(record));
    }

    @GetMapping("/notifications")
    @Operation(summary = "Notification history - filterable by channel/status/template/recipient, paginated")
    public ResponseEntity<PagedResponse<NotificationResponse>> search(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) String template,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        var results = recordRepository.search(channel, status, template, to, pageable)
                .map(NotificationResponse::from);
        return ResponseEntity.ok(PagedResponse.of(results));
    }
}
