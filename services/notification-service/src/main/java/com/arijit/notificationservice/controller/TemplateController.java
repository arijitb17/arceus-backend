package com.arijit.notificationservice.controller;

import com.arijit.notificationservice.dto.*;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.service.TemplateManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-only CRUD over {@link com.arijit.notificationservice.entity.NotificationTemplate}
 * - answers gap #4 from the backend-gaps doc. Same trust model as
 * NotificationController: only reachable by ROLE_ADMIN, same as every other
 * endpoint in this service (class-level security is inherited from being
 * under this service's SecurityConfig, but stated explicitly here too since
 * template content becomes real outbound email/SMS the moment it's used).
 */
@RestController
@RequestMapping("/notifications/templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Notification Templates", description = "Admin-only: CRUD for notification templates")
public class TemplateController {

    private final TemplateManagementService templateManagementService;

    @GetMapping
    @Operation(summary = "List templates, optionally filtered by channel (paginated)")
    public ResponseEntity<PagedResponse<TemplateResponse>> list(
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(templateManagementService.list(channel, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single template")
    public ResponseEntity<TemplateResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(templateManagementService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create a new template for a (channel, code) pair")
    public ResponseEntity<TemplateResponse> create(@Valid @RequestBody CreateTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateManagementService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a template's subject/body (channel/code are immutable)")
    public ResponseEntity<TemplateResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateRequest request) {
        return ResponseEntity.ok(templateManagementService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Enable or disable a template")
    public ResponseEntity<TemplateResponse> updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateTemplateStatusRequest request) {
        return ResponseEntity.ok(templateManagementService.updateStatus(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Permanently delete a template")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        templateManagementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
