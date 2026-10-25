package com.arijit.monitoringservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthService {
    private final DataSource dataSource;
    private final ConnectionFactory rabbitConnectionFactory;
    private final RedisConnectionFactory redisConnectionFactory;

    public String databaseStatus() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Self database health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    public String rabbitStatus() {
        try (var connection = rabbitConnectionFactory.createConnection()) {
            return connection.isOpen() ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Self RabbitMQ health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    public String cacheStatus() {
        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Self Redis health check failed (Redis is optional caching for this service): {}", e.getMessage());
            return "DOWN";
        }
    }

    public boolean isUp() {
        return "UP".equals(databaseStatus()) && "UP".equals(rabbitStatus());
    }
}
