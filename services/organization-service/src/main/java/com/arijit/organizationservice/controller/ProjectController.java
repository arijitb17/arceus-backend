package com.arijit.organizationservice.controller;

import com.arijit.organizationservice.dto.CreateProjectRequest;
import com.arijit.organizationservice.dto.ProjectResponse;
import com.arijit.organizationservice.entity.Project;
import com.arijit.organizationservice.entity.ProjectEnvironment;
import com.arijit.organizationservice.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/organizations/{organizationId}/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@PathVariable UUID organizationId,
                                                    @Valid @RequestBody CreateProjectRequest request,
                                                    Principal principal) {
        Project project = projectService.create(organizationId, request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping
    public List<ProjectResponse> list(@PathVariable UUID organizationId, Principal principal) {
        return projectService.list(organizationId, principal.getName()).stream()
                .map(ProjectResponse::from)
                .toList();
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable UUID organizationId, @PathVariable UUID projectId, Principal principal) {
        return ProjectResponse.from(projectService.get(organizationId, projectId, principal.getName()));
    }

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> delete(@PathVariable UUID organizationId, @PathVariable UUID projectId, Principal principal) {
        projectService.delete(organizationId, projectId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/environments")
    public List<EnvironmentSummary> environments(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                                   Principal principal) {
        return projectService.environments(organizationId, projectId, principal.getName()).stream()
                .map(EnvironmentSummary::from)
                .toList();
    }

    public record EnvironmentSummary(UUID id, String environment, String trackedBranch) {
        static EnvironmentSummary from(ProjectEnvironment env) {
            return new EnvironmentSummary(env.getId(), env.getEnvironment().name(), env.getTrackedBranch());
        }
    }
}
