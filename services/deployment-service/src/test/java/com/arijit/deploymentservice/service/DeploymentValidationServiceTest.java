package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.config.DeploymentProperties;
import com.arijit.deploymentservice.dto.CreateDeploymentRequest;
import com.arijit.deploymentservice.entity.Environment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * validateCreate() never touches the monitoring-service gRPC stub - it's
 * pure format validation - so this exercises it without standing up any
 * Spring/gRPC infrastructure, same "plain unit test, no mocks needed"
 * style as scheduler-service's JobValidationServiceTest.
 */
class DeploymentValidationServiceTest {

    private final DeploymentProperties properties = new DeploymentProperties(
            new DeploymentProperties.Approval(List.of("STAGING", "PRODUCTION"), false),
            new DeploymentProperties.Validation(
                    false,
                    "^[a-zA-Z0-9_.\\-]+$",
                    "^[a-zA-Z0-9_.\\-/]+:[a-zA-Z0-9_.\\-]+$",
                    Map.of(),
                    Map.of()
            ),
            new DeploymentProperties.Scheduling(true, "http://localhost:8089", 30000L),
            new DeploymentProperties.Internal("test-token"),
            new DeploymentProperties.GrpcClients("test-token", "test-token", "test-token")
    );

    private final DeploymentValidationService validator = new DeploymentValidationService(properties, null);

    private CreateDeploymentRequest request(String version, String dockerImage) {
        return new CreateDeploymentRequest("some-service", version, dockerImage, Environment.DEVELOPMENT,
                "someone@example.com", null, null, null, null);
    }

    @Test
    void validVersionAndImagePasses() {
        var result = validator.validateCreate(request("1.2.3", "registry.example.com/some-service:1.2.3"));
        assertTrue(result.passed());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void dockerImageWithoutTagIsRejected() {
        var result = validator.validateCreate(request("1.2.3", "registry.example.com/some-service"));
        assertFalse(result.passed());
    }

    @Test
    void blankVersionIsRejected() {
        var result = validator.validateCreate(request("", "registry.example.com/some-service:1.2.3"));
        assertFalse(result.passed());
    }

    @Test
    void versionWithSpacesIsRejected() {
        var result = validator.validateCreate(request("1.2.3 latest", "registry.example.com/some-service:1.2.3"));
        assertFalse(result.passed());
    }
}