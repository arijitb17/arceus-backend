package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.dto.HealthResponse;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

/**
 * GET /health's backing service - reports the scheduler's own operational
 * state (poller/worker pool/broker/database), not job-level business
 * metrics (that's GET /jobs/stats). A degraded RabbitMQ or database
 * doesn't take this service itself down; it's reported as DOWN/DEGRADED
 * in the response body so callers/monitoring can decide what to do.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthService {

    private final JobRepository jobRepository;
    private final ThreadPoolTaskExecutor jobTaskExecutor;
    private final ConnectionFactory rabbitConnectionFactory;

    public HealthResponse check() {
        String dbStatus = checkDatabase();
        String rabbitStatus = checkRabbit();
        long activeJobs = jobRepository.countByStatus(JobStatus.RUNNING);

        boolean healthy = "UP".equals(dbStatus) && "UP".equals(rabbitStatus);
        return new HealthResponse(
                healthy ? "UP" : "DEGRADED",
                "RUNNING",
                activeJobs,
                jobTaskExecutor.getPoolSize(),
                jobTaskExecutor.getActiveCount(),
                jobTaskExecutor.getThreadPoolExecutor().getQueue().size(),
                rabbitStatus,
                dbStatus
        );
    }

    private String checkDatabase() {
        try {
            jobRepository.count();
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
}
