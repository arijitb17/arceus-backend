package com.arijit.monitoringservice.controller;

import com.arijit.monitoringservice.dto.AlertRuleRequest;
import com.arijit.monitoringservice.dto.AlertRuleResponse;
import com.arijit.monitoringservice.dto.PagedResponse;
import com.arijit.monitoringservice.service.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert Rule Management")
public class AlertController {
    private final AlertRuleService alertRuleService;

    @GetMapping("/alerts")
    @Operation(summary = "List configured alert rules")
    public PagedResponse<AlertRuleResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return alertRuleService.list(pageable);
    }

    @PostMapping("/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create an alert rule (admin only)")
    public ResponseEntity<AlertRuleResponse> create(@Valid @RequestBody AlertRuleRequest request, Authentication authentication) {
        String createdBy = authentication != null ? authentication.getName() : "unknown";
        return ResponseEntity.status(HttpStatus.CREATED).body(alertRuleService.create(request, createdBy));
    }

    @PutMapping("/alerts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an alert rule (admin only)")
    public AlertRuleResponse update(@PathVariable UUID id, @Valid @RequestBody AlertRuleRequest request) {
        return alertRuleService.update(id, request);
    }

    @DeleteMapping("/alerts/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete an alert rule (admin only)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        alertRuleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
