package com.arijit.cacheservice.service;

import com.arijit.cacheservice.dto.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
@RequiredArgsConstructor
public class HealthService {

    private final RedisConnectionFactory redisConnectionFactory;

    public HealthResponse check() {
        String redisStatus;
        long memoryUsageBytes = 0;

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            redisStatus = "PONG".equalsIgnoreCase(pong) ? "UP" : "DEGRADED";

            Properties memoryInfo = connection.serverCommands().info("memory");
            if (memoryInfo != null) {
                String used = memoryInfo.getProperty("used_memory");
                memoryUsageBytes = used != null ? Long.parseLong(used.trim()) : 0;
            }
        } catch (Exception e) {
            log.warn("Redis health check failed: {}", e.getMessage());
            redisStatus = "DOWN";
        }

        String poolStatus = poolStatus();
        String overall = "UP".equals(redisStatus) && "UP".equals(poolStatus) ? "UP" : "DEGRADED";
        if ("DOWN".equals(redisStatus)) {
            overall = "DOWN";
        }

        return new HealthResponse(overall, redisStatus, poolStatus, memoryUsageBytes);
    }

    private String poolStatus() {
        if (redisConnectionFactory instanceof LettuceConnectionFactory) {
            try (RedisConnection probe = redisConnectionFactory.getConnection()) {
                return probe != null ? "UP" : "DOWN";
            } catch (Exception e) {
                return "DOWN";
            }
        }
        return "UNKNOWN";
    }
}
