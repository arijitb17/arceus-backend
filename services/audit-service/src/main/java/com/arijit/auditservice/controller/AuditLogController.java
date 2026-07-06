package com.arijit.auditservice.controller;

import com.arijit.auditservice.dto.AuditLogResponse;
import com.arijit.auditservice.dto.PagedResponse;
import com.arijit.auditservice.exception.ApiException;
import com.arijit.auditservice.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Logs", description = "Centralized, read-only audit trail built from every domain event on the platform")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @Operation(summary = "Search audit logs - filter by event type, actor email, date range, or free-text search over the description")
    public ResponseEntity<PagedResponse<AuditLogResponse>> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "occurredAt"));
        var results = auditLogRepository.search(eventType, actorEmail, from, to, q, pageable)
                .map(AuditLogResponse::from);
        return ResponseEntity.ok(PagedResponse.of(results));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single audit log entry by id")
    public ResponseEntity<AuditLogResponse> getById(@PathVariable UUID id) {
        return auditLogRepository.findById(id)
                .map(AuditLogResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ApiException("No audit log with id " + id, HttpStatus.NOT_FOUND));
    }
}
