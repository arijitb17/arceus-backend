package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.config.DeploymentProperties;
import com.arijit.deploymentservice.dto.CreateDeploymentRequest;
import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.arceus.grpc.monitoring.GetServiceHealthRequest;
import com.arijit.arceus.grpc.monitoring.MonitoringGrpcServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Everything the platform spec's "Deployment Validation" section asks for,
 * run once as a batch just before a deployment moves into DEPLOYING - see
 * DeploymentService#deploy. Every check appends to a shared
 * {@link ValidationResult} rather than failing fast, so a caller gets the
 * complete picture of what's wrong in one response instead of fixing one
 * problem at a time.
 * <p>
 * A finding is an <b>error</b> (always blocking) when it's something this
 * service can verify with certainty from data it owns (malformed version/
 * image string, a required env var genuinely absent). A finding is a
 * <b>warning</b> (blocking only when
 * {@code arceus.deployment.validation.strict=true}) when it depends on
 * another service's live state that this service can't fully vouch for
 * (monitoring-service reachable but reports a dependency DOWN/DEGRADED, or
 * monitoring-service itself unreachable) - defaulting to non-strict means a
 * monitoring-service outage never by itself blocks every deployment on the
 * platform.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeploymentValidationService {

    private final DeploymentProperties properties;
    private final MonitoringGrpcServiceGrpc.MonitoringGrpcServiceBlockingStub monitoringStub;

    /** Cheap, synchronous checks run at creation time - nothing that depends on another service's live state. */
    public ValidationResult validateCreate(CreateDeploymentRequest request) {
        List<String> errors = new ArrayList<>();

        if (!matches(properties.validation().versionPattern(), request.serviceVersion())) {
            errors.add("serviceVersion '" + request.serviceVersion() + "' does not match required pattern "
                    + properties.validation().versionPattern());
        }
        if (!matches(properties.validation().dockerImagePattern(), request.dockerImage())) {
            errors.add("dockerImage '" + request.dockerImage() + "' does not match required pattern "
                    + properties.validation().dockerImagePattern() + " (expected repository:tag)");
        }

        return errors.isEmpty() ? ValidationResult.ok() : new ValidationResult(false, List.of(), errors);
    }

    /**
     * The full pre-deploy pass: version/config format (re-checked in case
     * properties changed between create and deploy), required environment
     * variables for the target environment, and every declared
     * dependency's current health per monitoring-service.
     */
    public ValidationResult validateForDeploy(Deployment deployment) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!matches(properties.validation().versionPattern(), deployment.getServiceVersion())) {
            errors.add("serviceVersion '" + deployment.getServiceVersion() + "' does not match required pattern");
        }
        if (!matches(properties.validation().dockerImagePattern(), deployment.getDockerImage())) {
            errors.add("dockerImage '" + deployment.getDockerImage() + "' does not match required pattern");
        }

        checkRequiredEnvVars(deployment, errors);
        checkDependencies(deployment, errors, warnings);

        boolean passed = errors.isEmpty() && (warnings.isEmpty() || !properties.validation().strict());
        return new ValidationResult(passed, warnings, errors);
    }

    private void checkRequiredEnvVars(Deployment deployment, List<String> errors) {
        List<String> required = properties.validation().requiredEnvVars()
                .getOrDefault(deployment.getEnvironment().name(), List.of());
        for (String key : required) {
            String value = System.getenv(key);
            if (value == null || value.isBlank()) {
                errors.add("required configuration '" + key + "' is not available for environment "
                        + deployment.getEnvironment());
            }
        }
    }

    private void checkDependencies(Deployment deployment, List<String> errors, List<String> warnings) {
        List<String> dependencies = properties.validation().dependencies()
                .getOrDefault(deployment.getServiceName(), List.of());
        for (String dependency : dependencies) {
            try {
                var response = monitoringStub.getServiceHealth(
                        GetServiceHealthRequest.newBuilder().setServiceName(dependency).build());
                if (!response.getFound()) {
                    warnings.add("dependency '" + dependency + "' is not known to monitoring-service yet");
                    continue;
                }
                String status = response.getHealth().getStatus();
                if (!"UP".equals(status)) {
                    String message = "dependency '" + dependency + "' is currently " + status;
                    if (properties.validation().strict()) {
                        errors.add(message);
                    } else {
                        warnings.add(message);
                    }
                }
            } catch (StatusRuntimeException e) {
                warnings.add("could not reach monitoring-service to check dependency '" + dependency
                        + "': " + e.getStatus().getCode());
                log.warn("Dependency health check for {} failed: {}", dependency, e.getStatus());
            }
        }
    }

    private boolean matches(String pattern, String value) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        return value != null && Pattern.matches(pattern, value);
    }
}
