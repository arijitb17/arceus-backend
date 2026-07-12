package com.arijit.deploymentservice.repository;

import com.arijit.deploymentservice.entity.Deployment;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.entity.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    Page<Deployment> findByServiceName(String serviceName, Pageable pageable);

    Page<Deployment> findByEnvironment(Environment environment, Pageable pageable);

    Page<Deployment> findByServiceNameAndEnvironment(String serviceName, Environment environment, Pageable pageable);

    Page<Deployment> findByStatus(DeploymentStatus status, Pageable pageable);

    Page<Deployment> findByServiceNameAndStatus(String serviceName, DeploymentStatus status, Pageable pageable);

    Page<Deployment> findByEnvironmentAndStatus(Environment environment, DeploymentStatus status, Pageable pageable);

    Page<Deployment> findByServiceNameAndEnvironmentAndStatus(
            String serviceName, Environment environment, DeploymentStatus status, Pageable pageable);

    /** The currently live version for a service+environment, if any - at most one row should ever be RUNNING per (serviceName, environment). */
    Optional<Deployment> findFirstByServiceNameAndEnvironmentAndStatusOrderByCompletedAtDesc(
            String serviceName, Environment environment, DeploymentStatus status);

    /**
     * The currently live version for a service across every environment -
     * used by MonitoringHealthEventsConsumer, since monitoring-service
     * tracks a service's health by name only (this platform runs one
     * instance of each service today, not one per environment).
     */
    Optional<Deployment> findFirstByServiceNameAndStatusOrderByCompletedAtDesc(String serviceName, DeploymentStatus status);

    /**
     * Every deployment that was ever live for this service+environment
     * (RUNNING now, or ROLLED_BACK later), most recent first - this is
     * what VersionService walks to find "the previous version" and to
     * resolve an explicit target-version rollback.
     */
    List<Deployment> findByServiceNameAndEnvironmentAndStatusInOrderByCompletedAtDesc(
            String serviceName, Environment environment, List<DeploymentStatus> statuses);

    long countByStatus(DeploymentStatus status);
}
