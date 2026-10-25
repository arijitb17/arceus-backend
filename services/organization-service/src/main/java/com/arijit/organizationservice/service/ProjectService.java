package com.arijit.organizationservice.service;

import com.arijit.organizationservice.dto.CreateProjectRequest;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.Project;
import com.arijit.organizationservice.entity.ProjectEnvironment;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.ProjectEnvironmentRepository;
import com.arijit.organizationservice.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final OrgAccessGuard accessGuard;
    private final OrganizationEventPublisher eventPublisher;

    @Transactional
    public Project create(UUID organizationId, CreateProjectRequest request, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);

        if (projectRepository.existsByOrganizationIdAndSlug(organizationId, request.slug())) {
            log.warn("Project create rejected: slug '{}' already exists in org={}", request.slug(), organizationId);
            throw new ApiException("A project with slug '" + request.slug() + "' already exists in this organization", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        Project project = projectRepository.save(Project.builder()
                .organizationId(organizationId)
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .createdBy(callerUserId)
                .createdAt(now)
                .updatedAt(now)
                .build());

        for (EnvironmentType env : EnvironmentType.values()) {
            environmentRepository.save(ProjectEnvironment.builder()
                    .projectId(project.getId())
                    .environment(env)
                    .trackedBranch(env == EnvironmentType.PRODUCTION ? "main" : null)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }

        log.info("Project created: projectId={} org={} slug={} by={}", project.getId(), organizationId, project.getSlug(), callerUserId);
        eventPublisher.projectCreated(project, callerUserId);
        return project;
    }

    public List<Project> list(UUID organizationId, String callerUserId) {
        accessGuard.requireMember(organizationId, callerUserId);
        return projectRepository.findByOrganizationIdAndDeletedAtIsNull(organizationId);
    }

    public Project get(UUID organizationId, UUID projectId, String callerUserId) {
        accessGuard.requireMember(organizationId, callerUserId);
        return findActiveOrThrow(organizationId, projectId);
    }

    public List<ProjectEnvironment> environments(UUID organizationId, UUID projectId, String callerUserId) {
        accessGuard.requireMember(organizationId, callerUserId);
        findActiveOrThrow(organizationId, projectId);
        return environmentRepository.findByProjectId(projectId);
    }

    @Transactional
    public void delete(UUID organizationId, UUID projectId, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        Project project = findActiveOrThrow(organizationId, projectId);
        project.setDeletedAt(Instant.now());
        projectRepository.save(project);
        log.info("Project deleted: projectId={} org={} by={}", projectId, organizationId, callerUserId);
        eventPublisher.projectDeleted(project.getId(), organizationId, callerUserId);
    }

    Project findActiveOrThrow(UUID organizationId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.debug("Project lookup failed: projectId={}", projectId);
                    return new ApiException("Project not found", HttpStatus.NOT_FOUND);
                });
        if (project.getDeletedAt() != null || !project.getOrganizationId().equals(organizationId)) {
            log.debug("Project lookup rejected: projectId={} deleted or not in org={}", projectId, organizationId);
            throw new ApiException("Project not found", HttpStatus.NOT_FOUND);
        }
        return project;
    }
}
