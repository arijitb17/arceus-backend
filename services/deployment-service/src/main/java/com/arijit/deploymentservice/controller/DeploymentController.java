package com.arijit.deploymentservice.controller;

import com.arijit.deploymentservice.dto.CreateDeploymentRequest;
import com.arijit.deploymentservice.dto.DeploymentResponse;
import com.arijit.deploymentservice.dto.PagedResponse;
import com.arijit.deploymentservice.dto.RollbackRequest;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;
import com.arijit.deploymentservice.service.DeploymentService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The platform spec's REST API surface, minus {@code GET /health} (see
 * HealthController instead). Create/read are available to any
 * authenticated caller (human via the Gateway, or another service via
 * X-Internal-Token); approve/deploy/rollback/cancel - anything that
 * actually changes what's running - require ROLE_ADMIN, same posture
 * monitoring-service uses for alert-rule mutation.
 */
@RestController
@RequestMapping("/deployments")
@RequiredArgsConstructor
@Tag(name = "Deployments", description = "Create, approve, deploy, roll back, and track every Arceus service deployment")
public class DeploymentController {

    private final DeploymentService deploymentService;

    @PostMapping
    @Operation(summary = "Create a deployment")
    public ResponseEntity<DeploymentResponse> create(@Valid @RequestBody CreateDeploymentRequest request) {
        var deployment = deploymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeploymentResponse.from(deployment));
    }

    @GetMapping
    @Operation(summary = "List deployments, optionally filtered by service, environment, and/or status")
    public PagedResponse<DeploymentResponse> list(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) DeploymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.of(deploymentService.list(serviceName, environment, status, pageable)
                .map(DeploymentResponse::from));
    }

    @GetMapping("/history")
    @Operation(summary = "Chronological release history, optionally filtered by service and/or environment")
    public PagedResponse<DeploymentResponse> history(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) Environment environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return PagedResponse.of(deploymentService.history(serviceName, environment, pageable)
                .map(DeploymentResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single deployment by id")
    public DeploymentResponse get(@PathVariable UUID id) {
        return DeploymentResponse.from(deploymentService.getOrThrow(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a deployment awaiting approval (admin only)")
    public DeploymentResponse approve(@PathVariable UUID id, Authentication authentication) {
        String approvedBy = authentication != null ? authentication.getName() : "unknown";
        return DeploymentResponse.from(deploymentService.approve(id, approvedBy));
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deploy a created or approved deployment (admin only)")
    public DeploymentResponse deploy(@PathVariable UUID id) {
        return DeploymentResponse.from(deploymentService.deploy(id));
    }

    @PostMapping("/{id}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Roll back the given service+environment to an earlier version (admin only)")
    public ResponseEntity<DeploymentResponse> rollback(@PathVariable UUID id, @Valid @RequestBody RollbackRequest request,
                                                        Authentication authentication) {
        RollbackRequest effective = request.initiatedBy() != null && !request.initiatedBy().isBlank()
                ? request
                : new RollbackRequest(request.targetVersion(),
                        authentication != null ? authentication.getName() : "unknown", request.correlationId());
        var deployment = deploymentService.rollback(id, effective);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeploymentResponse.from(deployment));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancel a deployment that hasn't started deploying yet (admin only)")
    public DeploymentResponse cancel(@PathVariable UUID id) {
        return DeploymentResponse.from(deploymentService.cancel(id));
    }
}
