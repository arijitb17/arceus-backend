package com.arijit.deploymentservice.service;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.monitoring.MonitoringGrpcServiceGrpc;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import com.arijit.deploymentservice.dto.HealthResponse;
import com.arijit.deploymentservice.entity.DeploymentStatus;
import com.arijit.deploymentservice.repository.DeploymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * GET /health's backing service. Database and RabbitMQ DOWN both take the
 * overall status to DOWN, since neither is optional for this service to
 * do its job (persisting deployments; publishing deployment.* events).
 * monitoring-service/scheduler-service being unreachable is reported but
 * only degrades the overall status - deployment-service can still create,
 * approve, and cancel deployments without either of them; it just can't
 * do dependency-health validation or scheduled deploys until they're back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthService {

    private final DeploymentRepository repository;
    private final ConnectionFactory rabbitConnectionFactory;
    private final MonitoringGrpcServiceGrpc.MonitoringGrpcServiceBlockingStub monitoringStub;
    private final SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;

    public HealthResponse check() {
        String dbStatus = checkDatabase();
        String rabbitStatus = checkRabbit();
        String monitoringStatus = checkPing(() -> monitoringStub.ping(PingRequest.newBuilder().build()));
        String schedulerStatus = checkPing(() -> schedulerStub.ping(PingRequest.newBuilder().build()));

        String overall;
        if ("DOWN".equals(dbStatus) || "DOWN".equals(rabbitStatus)) {
            overall = "DOWN";
        } else if ("DOWN".equals(monitoringStatus) || "DOWN".equals(schedulerStatus)) {
            overall = "DEGRADED";
        } else {
            overall = "UP";
        }

        long active = repository.countByStatus(DeploymentStatus.DEPLOYING) + repository.countByStatus(DeploymentStatus.RUNNING);
        long pendingApprovals = repository.countByStatus(DeploymentStatus.PENDING);

        return new HealthResponse(overall, active, pendingApprovals, dbStatus, rabbitStatus, monitoringStatus, schedulerStatus);
    }

    private String checkDatabase() {
        try {
            repository.count();
            return "UP";
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkRabbit() {
        try (var connection = rabbitConnectionFactory.createConnection()) {
            return connection.isOpen() ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("RabbitMQ health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkPing(Runnable ping) {
        try {
            ping.run();
            return "UP";
        } catch (Exception e) {
            log.warn("gRPC ping failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}
