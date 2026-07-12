package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;
import com.arijit.deploymentservice.repository.DeploymentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VersionServiceTest {

    private final DeploymentRepository repository = mock(DeploymentRepository.class);
    private final VersionService versionService = new VersionService(repository);

    private Deployment deployment(String version, DeploymentStatus status, Instant completedAt) {
        return Deployment.builder()
                .id(UUID.randomUUID())
                .serviceName("some-service")
                .serviceVersion(version)
                .dockerImage("registry/some-service:" + version)
                .environment(Environment.PRODUCTION)
                .status(status)
                .createdBy("someone@example.com")
                .completedAt(completedAt)
                .build();
    }

    @Test
    void previousVersionIsTheOneBeforeWhicheverIsCurrentlyRunning() {
        Deployment current = deployment("2.0.0", DeploymentStatus.RUNNING, Instant.parse("2026-01-02T00:00:00Z"));
        Deployment previous = deployment("1.0.0", DeploymentStatus.ROLLED_BACK, Instant.parse("2026-01-01T00:00:00Z"));

        when(repository.findByServiceNameAndEnvironmentAndStatusInOrderByCompletedAtDesc(
                eq("some-service"), eq(Environment.PRODUCTION), any()))
                .thenReturn(List.of(current, previous));

        var result = versionService.previousVersion("some-service", Environment.PRODUCTION);

        assertTrue(result.isPresent());
        assertEquals("1.0.0", result.get().getServiceVersion());
    }

    @Test
    void resolveTargetVersionFindsAMatchingEarlierVersion() {
        Deployment v1 = deployment("1.0.0", DeploymentStatus.ROLLED_BACK, Instant.parse("2026-01-01T00:00:00Z"));
        Deployment v2 = deployment("2.0.0", DeploymentStatus.RUNNING, Instant.parse("2026-01-02T00:00:00Z"));

        when(repository.findByServiceNameAndEnvironmentAndStatusInOrderByCompletedAtDesc(
                eq("some-service"), eq(Environment.PRODUCTION), any()))
                .thenReturn(List.of(v2, v1));

        Deployment resolved = versionService.resolveTargetVersion("some-service", Environment.PRODUCTION, "1.0.0");

        assertEquals("1.0.0", resolved.getServiceVersion());
    }
}
