package com.arijit.cacheservice.service;

import com.arijit.cacheservice.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock RedisConnectionFactory genericFactory;
    @Mock LettuceConnectionFactory lettuceFactory;
    @Mock RedisConnection connection;
    @Mock RedisServerCommands server;

    private void stubConnection(RedisConnectionFactory factory, String ping, String usedMemory) {
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn(ping);
        when(connection.serverCommands()).thenReturn(server);
        Properties p = new Properties();
        if (usedMemory != null) p.setProperty("used_memory", usedMemory);
        when(server.info("memory")).thenReturn(p);
    }

    @Test
    void lettuceWithPongIsUp() {
        stubConnection(lettuceFactory, "PONG", "4096");

        HealthResponse r = new HealthService(lettuceFactory).check();

        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.redisStatus()).isEqualTo("UP");
        assertThat(r.connectionPoolStatus()).isEqualTo("UP");
        assertThat(r.memoryUsageBytes()).isEqualTo(4096);
    }

    @Test
    void nonLettuceFactoryReportsUnknownPoolAndDegradedOverall() {
        stubConnection(genericFactory, "pong", null);

        HealthResponse r = new HealthService(genericFactory).check();

        assertThat(r.redisStatus()).isEqualTo("UP");
        assertThat(r.connectionPoolStatus()).isEqualTo("UNKNOWN");
        assertThat(r.status()).isEqualTo("DEGRADED");
        assertThat(r.memoryUsageBytes()).isZero();
    }

    @Test
    void unexpectedPingReplyIsDegraded() {
        stubConnection(lettuceFactory, "WHAT", "1");

        HealthResponse r = new HealthService(lettuceFactory).check();

        assertThat(r.redisStatus()).isEqualTo("DEGRADED");
        assertThat(r.status()).isEqualTo("DEGRADED");
    }

    @Test
    void connectionFailureIsDown() {
        when(lettuceFactory.getConnection()).thenThrow(new IllegalStateException("refused"));

        HealthResponse r = new HealthService(lettuceFactory).check();

        assertThat(r.redisStatus()).isEqualTo("DOWN");
        assertThat(r.connectionPoolStatus()).isEqualTo("DOWN");
        assertThat(r.status()).isEqualTo("DOWN");
    }
}
