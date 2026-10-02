package com.arijit.organizationservice.service;

import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.entity.ModuleEnablement;
import com.arijit.organizationservice.entity.ModuleStatus;
import com.arijit.organizationservice.entity.ModuleType;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.ModuleEnablementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The Developer Portal-facing half of the module-provisioning workflow
 * (architecture design §4). Enabling or disabling a module calls straight
 * through to {@link ModuleProvisioningClient} - the one service that
 * ModuleType maps to - and resolves the row to its terminal state
 * (ACTIVE/FAILED or DISABLED/ACTIVE) in the same request, instead of
 * leaving it at PENDING waiting on a deployment-service event pipeline
 * that isn't part of this platform. See ModuleProvisioningClient's javadoc
 * for why a direct synchronous call replaced that pipeline, and
 * OrganizationEventPublisher for why moduleEnableRequested/
 * moduleDisableRequested are still published even though nothing
 * currently consumes them - they're a durable audit trail of the request
 * itself, independent of how it eventually got fulfilled.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModuleEnablementService {

    private final ModuleEnablementRepository repository;
    private final OrgAccessGuard accessGuard;
    private final ProjectService projectService;
    private final OrganizationEventPublisher eventPublisher;
    private final ModuleProvisioningClient provisioningClient;

    public List<ModuleEnablement> list(UUID organizationId, UUID projectId, EnvironmentType environment, String callerUserId) {
        accessGuard.requireMember(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);
        return repository.findByProjectIdAndEnvironment(projectId, environment);
    }

    @Transactional
    public ModuleEnablement enable(UUID organizationId, UUID projectId, EnvironmentType environment,
                                    ModuleType module, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);

        ModuleEnablement enablement = repository.findByProjectIdAndEnvironmentAndModule(projectId, environment, module)
                .orElse(null);

        if (enablement != null && (enablement.getStatus() == ModuleStatus.ACTIVE
                || enablement.getStatus() == ModuleStatus.PENDING
                || enablement.getStatus() == ModuleStatus.PROVISIONING)) {
            throw new ApiException(module + " is already enabled or being provisioned for this environment", HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        if (enablement == null) {
            enablement = ModuleEnablement.builder()
                    .projectId(projectId)
                    .environment(environment)
                    .module(module)
                    .status(ModuleStatus.PROVISIONING)
                    .requestedBy(callerUserId)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        } else {
            // Re-enabling after a FAILED or DISABLED state - same row, fresh request.
            enablement.setStatus(ModuleStatus.PROVISIONING);
            enablement.setRequestedBy(callerUserId);
            enablement.setLastError(null);
            enablement.setUpdatedAt(now);
        }
        repository.save(enablement);
        eventPublisher.moduleEnableRequested(projectId, environment, module, callerUserId);

        try {
            String resourceReference = provisioningClient.provision(module, projectId, environment, callerUserId);
            log.info("Provisioned {} for project={} environment={} resourceReference={}",
                    module, projectId, environment, resourceReference);
            return markActive(projectId, environment, module);
        } catch (Exception ex) {
            log.warn("Failed to provision {} for project={} environment={}: {}",
                    module, projectId, environment, ex.getMessage());
            return markFailed(projectId, environment, module, ex.getMessage());
        }
    }

    @Transactional
    public ModuleEnablement disable(UUID organizationId, UUID projectId, EnvironmentType environment,
                                     ModuleType module, String callerUserId) {
        accessGuard.requireManager(organizationId, callerUserId);
        projectService.findActiveOrThrow(organizationId, projectId);

        ModuleEnablement enablement = repository.findByProjectIdAndEnvironmentAndModule(projectId, environment, module)
                .orElseThrow(() -> new ApiException(module + " is not enabled for this environment", HttpStatus.NOT_FOUND));

        if (enablement.getStatus() != ModuleStatus.ACTIVE) {
            throw new ApiException("Can only disable a module that is currently ACTIVE (found " + enablement.getStatus() + ")", HttpStatus.CONFLICT);
        }

        enablement.setStatus(ModuleStatus.DISABLING);
        enablement.setUpdatedAt(Instant.now());
        repository.save(enablement);
        eventPublisher.moduleDisableRequested(projectId, environment, module, callerUserId);

        try {
            provisioningClient.deprovision(module, projectId, environment, callerUserId);
            log.info("Deprovisioned {} for project={} environment={}", module, projectId, environment);
            return markDisabled(projectId, environment, module);
        } catch (Exception ex) {
            // Deprovisioning failed - leave it ACTIVE rather than silently
            // dropping the module into an ambiguous DISABLING-forever state;
            // the caller can see the error and retry.
            log.warn("Failed to deprovision {} for project={} environment={}: {}",
                    module, projectId, environment, ex.getMessage());
            enablement.setStatus(ModuleStatus.ACTIVE);
            enablement.setLastError(ex.getMessage());
            enablement.setUpdatedAt(Instant.now());
            repository.save(enablement);
            throw new ApiException("Failed to disable " + module + ": " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ---------- Terminal-state transitions ----------

    private ModuleEnablement markActive(UUID projectId, EnvironmentType environment, ModuleType module) {
        return transition(projectId, environment, module, enablement -> {
            enablement.setStatus(ModuleStatus.ACTIVE);
            enablement.setLastError(null);
        });
    }

    private ModuleEnablement markFailed(UUID projectId, EnvironmentType environment, ModuleType module, String errorMessage) {
        return transition(projectId, environment, module, enablement -> {
            enablement.setStatus(ModuleStatus.FAILED);
            enablement.setLastError(errorMessage);
        });
    }

    private ModuleEnablement markDisabled(UUID projectId, EnvironmentType environment, ModuleType module) {
        return transition(projectId, environment, module, enablement -> {
            enablement.setStatus(ModuleStatus.DISABLED);
            enablement.setLastError(null);
        });
    }

    private ModuleEnablement transition(UUID projectId, EnvironmentType environment, ModuleType module,
                                         java.util.function.Consumer<ModuleEnablement> mutator) {
        ModuleEnablement enablement = repository.findByProjectIdAndEnvironmentAndModule(projectId, environment, module)
                .orElseThrow(() -> new ApiException(module + " enablement not found for this environment", HttpStatus.NOT_FOUND));
        mutator.accept(enablement);
        enablement.setUpdatedAt(Instant.now());
        return repository.save(enablement);
    }
}
