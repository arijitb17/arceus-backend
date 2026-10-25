package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.dto.HealthResponse;
import com.arijit.featureflagservice.repository.FeatureFlagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock FeatureFlagRepository repo;
    @Mock ConnectionFactory rabbitFactory;
    @Mock Connection rabbitConnection;
    @Mock StringRedisTemplate redis;
    @Mock RedisConnectionFactory redisFactory;
    @Mock RedisConnection redisConnection;

    private HealthService service;

    @BeforeEach
    void setUp() {
        service = new HealthService(repo, rabbitFactory, redis);
        lenient().when(rabbitFactory.createConnection()).thenReturn(rabbitConnection);
        lenient().when(rabbitConnection.isOpen()).thenReturn(true);
        lenient().when(redis.getConnectionFactory()).thenReturn(redisFactory);
        lenient().when(redisFactory.getConnection()).thenReturn(redisConnection);
    }

    @Test
    void everythingUp() {
        HealthResponse r = service.check();

        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.databaseStatus()).isEqualTo("UP");
        assertThat(r.cacheStatus()).isEqualTo("UP");
        assertThat(r.rabbitMqStatus()).isEqualTo("UP");
    }

    @Test
    void cacheDownOnlyDegrades() {
        when(redisConnection.ping()).thenThrow(new IllegalStateException("no redis"));

        HealthResponse r = service.check();

        assertThat(r.status()).isEqualTo("DEGRADED");
        assertThat(r.cacheStatus()).isEqualTo("DOWN");
    }

    @Test
    void databaseDownIsDown() {
        when(repo.count()).thenThrow(new IllegalStateException("pg"));

        assertThat(service.check().status()).isEqualTo("DOWN");
        assertThat(service.check().databaseStatus()).isEqualTo("DOWN");
    }

    @Test
    void closedOrFailingRabbitIsDown() {
        when(rabbitConnection.isOpen()).thenReturn(false);
        assertThat(service.check().rabbitMqStatus()).isEqualTo("DOWN");
        assertThat(service.check().status()).isEqualTo("DOWN");

        when(rabbitFactory.createConnection()).thenThrow(new IllegalStateException("amqp"));
        assertThat(service.check().rabbitMqStatus()).isEqualTo("DOWN");
    }
}
