package com.arijit.deploymentservice.service;

import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;
import com.arijit.deploymentservice.exception.ApiException;
import com.arijit.deploymentservice.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The platform spec's "Version Management" section: current/previous/
 * rollback version resolution for a service+environment. Deliberately not
 * a separate table - a service+environment's entire release history
 * (current version, every version that was ever live, release notes for
 * each) is fully derivable from the {@link Deployment} rows themselves,
 * queried chronologically by {@code completedAt}.
 */
@Service
@RequiredArgsConstructor
public class VersionService {

    /** A deployment counts as "was live" if it's currently RUNNING, or was RUNNING before being superseded by a rollback. */
    private static final List<DeploymentStatus> WAS_LIVE_STATUSES = List.of(DeploymentStatus.RUNNING, DeploymentStatus.ROLLED_BACK);

    private final DeploymentRepository repository;

    /** The deployment currently live for this service+environment, if any. */
    public Optional<Deployment> currentVersion(String serviceName, Environment environment) {
        return repository.findFirstByServiceNameAndEnvironmentAndStatusOrderByCompletedAtDesc(
                serviceName, environment, DeploymentStatus.RUNNING);
    }

    /**
     * The deployment that was live immediately before whichever one is
     * live now (or, if nothing is currently RUNNING, the most recently
     * superseded one) - what a rollback with no explicit target version
     * falls back to.
     */
    public Optional<Deployment> previousVersion(String serviceName, Environment environment) {
        List<Deployment> history = repository.findByServiceNameAndEnvironmentAndStatusInOrderByCompletedAtDesc(
                serviceName, environment, WAS_LIVE_STATUSES);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        // history[0] is whatever is/was most recently live; the "previous" one is the next one back.
        if (history.get(0).getStatus() == DeploymentStatus.RUNNING) {
            return history.size() > 1 ? Optional.of(history.get(1)) : Optional.empty();
        }
        return Optional.of(history.get(0));
    }

    /**
     * A specific earlier version to roll back to, resolved from
     * everything that was ever live for this service+environment.
     */
    public Deployment resolveTargetVersion(String serviceName, Environment environment, String targetVersion) {
        return repository.findByServiceNameAndEnvironmentAndStatusInOrderByCompletedAtDesc(
                        serviceName, environment, WAS_LIVE_STATUSES)
                .stream()
                .filter(d -> d.getServiceVersion().equals(targetVersion))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        "Version '" + targetVersion + "' was never live for " + serviceName + " in " + environment,
                        HttpStatus.NOT_FOUND));
    }
}
