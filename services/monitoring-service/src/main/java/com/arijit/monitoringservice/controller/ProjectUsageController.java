package com.arijit.monitoringservice.controller;

import com.arijit.arceus.grpc.organization.CheckManagerAccessRequest;
import com.arijit.arceus.grpc.organization.OrganizationAccessGrpcServiceGrpc;
import com.arijit.monitoringservice.dto.ProjectUsageResponse;
import com.arijit.monitoringservice.dto.UsageDayPoint;
import com.arijit.monitoringservice.entity.UsageRollup;
import com.arijit.monitoringservice.exception.ApiException;
import com.arijit.monitoringservice.repository.UsageRollupRepository;
import io.grpc.StatusRuntimeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects/{projectId}/usage")
@RequiredArgsConstructor
@Tag(name = "Project Usage", description = "Developer-facing, project-scoped request volume (usage/logs work)")
public class ProjectUsageController {
    private final UsageRollupRepository usageRollupRepository;
    private final OrganizationAccessGrpcServiceGrpc.OrganizationAccessGrpcServiceBlockingStub organizationAccessStub;

    @GetMapping
    @Operation(summary = "Daily request volume for a project, by route - the last 30 days by default")
    public ResponseEntity<ProjectUsageResponse> usage(
            @RequestParam String organizationId,
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "PRODUCTION") String environment,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Principal principal
    ) {
        requireMember(organizationId, principal.getName());

        LocalDate rangeTo = to != null ? to : LocalDate.now();
        LocalDate rangeFrom = from != null ? from : rangeTo.minusDays(29);

        List<UsageRollup> rollups = usageRollupRepository
                .findByProjectIdAndEnvironmentAndDayBetweenOrderByDayAsc(projectId, environment, rangeFrom, rangeTo);

        List<UsageDayPoint> points = rollups.stream()
                .map(r -> new UsageDayPoint(r.getDay(), r.getRoute(), r.getRequestCount(), r.getErrorCount()))
                .toList();

        long totalRequests = rollups.stream().mapToLong(UsageRollup::getRequestCount).sum();
        long totalErrors = rollups.stream().mapToLong(UsageRollup::getErrorCount).sum();

        return ResponseEntity.ok(new ProjectUsageResponse(rangeFrom, rangeTo, totalRequests, totalErrors, points));
    }

    private void requireMember(String organizationId, String callerEmail) {
        try {
            var response = organizationAccessStub.checkMemberAccess(CheckManagerAccessRequest.newBuilder()
                    .setOrganizationId(organizationId)
                    .setUserEmail(callerEmail)
                    .build());
            if (!response.getAuthorized()) {
                throw new ApiException("Not a member of this organization", HttpStatus.FORBIDDEN);
            }
        } catch (StatusRuntimeException e) {
            throw new ApiException("Could not verify organization access (organization-service unreachable)", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
