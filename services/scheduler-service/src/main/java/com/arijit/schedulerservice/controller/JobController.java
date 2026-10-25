package com.arijit.schedulerservice.controller;

import com.arijit.schedulerservice.dto.*;
import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.repository.JobExecutionHistoryRepository;
import com.arijit.schedulerservice.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Register, inspect, and manage scheduled/background jobs")
public class JobController {
    private final JobService jobService;
    private final JobExecutionHistoryRepository historyRepository;

    @PostMapping
    @Operation(summary = "Register a new job - one-time, delayed, cron, or recurring, delivered via HTTP callback or RabbitMQ publish")
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        Job job = jobService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single job by id")
    public ResponseEntity<JobResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(JobResponse.from(jobService.getOrThrow(id)));
    }

    @GetMapping
    @Operation(summary = "List jobs, optionally filtered by owning service and/or status, paginated")
    public ResponseEntity<PagedResponse<JobResponse>> list(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        var results = jobService.list(serviceName, status, pageable).map(JobResponse::from);
        return ResponseEntity.ok(PagedResponse.of(results));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Execution history (one row per attempt) for a job")
    public ResponseEntity<PagedResponse<JobHistoryResponse>> history(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        jobService.getOrThrow(id);
        var pageable = PageRequest.of(page, Math.min(size, 100));
        var results = historyRepository.findByJobIdOrderByStartedAtDesc(id, pageable).map(JobHistoryResponse::from);
        return ResponseEntity.ok(PagedResponse.of(results));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a job's mutable fields (payload/priority/retry policy/expiry) - not its trigger shape")
    public ResponseEntity<JobResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateJobRequest request) {
        return ResponseEntity.ok(JobResponse.from(jobService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a job (idempotent - a job that's already terminal is left as-is)")
    public ResponseEntity<JobResponse> delete(@PathVariable UUID id) {
        Job job = jobService.getOrThrow(id);
        if (job.getStatus().isTerminal()) {
            return ResponseEntity.ok(JobResponse.from(job));
        }
        return ResponseEntity.ok(JobResponse.from(jobService.cancel(id)));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause a SCHEDULED/RETRYING job - excluded from pickup until resumed")
    public ResponseEntity<JobResponse> pause(@PathVariable UUID id) {
        return ResponseEntity.ok(JobResponse.from(jobService.pause(id)));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume a PAUSED job")
    public ResponseEntity<JobResponse> resume(@PathVariable UUID id) {
        return ResponseEntity.ok(JobResponse.from(jobService.resume(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a job - terminal, cannot be resumed")
    public ResponseEntity<JobResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(JobResponse.from(jobService.cancel(id)));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Request an immediate retry of a FAILED/RETRYING job (published via RabbitMQ, applied asynchronously - see RetryRequestConsumer)")
    public ResponseEntity<JobResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(JobResponse.from(jobService.requestRetry(id)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Live counts by status, dead-letter count, and worker pool utilization")
    public ResponseEntity<JobStatsResponse> stats() {
        return ResponseEntity.ok(jobService.stats());
    }
}
