package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.dto.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.arijit.featureflagservice.repository.FeatureFlagRepository;

/**
 * Cache being DOWN never takes the overall status below DEGRADED (see FlagCacheService - a missing
 * cache just means slower reads from Postgres), whereas the database or RabbitMQ being DOWN means
 * this service can no longer do its actual job and is reported DOWN overall.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HealthService {
    private final FeatureFlagRepository flagRepository;
    private final ConnectionFactory rabbitConnectionFactory;
    private final StringRedisTemplate redisTemplate;

    public HealthResponse check() {
        String dbStatus = checkDatabase();
        String rabbitStatus = checkRabbit();
        String cacheStatus = checkCache();

        String overall;
        if ("DOWN".equals(dbStatus) || "DOWN".equals(rabbitStatus)) {
            overall = "DOWN";
        } else if ("DOWN".equals(cacheStatus)) {
            overall = "DEGRADED";
        } else {
            overall = "UP";
        }

        return new HealthResponse(overall, dbStatus, cacheStatus, rabbitStatus);
    }

    private String checkDatabase() {
        try {
            flagRepository.count();
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

    private String checkCache() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return "UP";
        } catch (Exception e) {
            log.warn("Cache health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}
