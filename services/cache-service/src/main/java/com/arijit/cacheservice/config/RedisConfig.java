package com.arijit.cacheservice.config;

import lombok.RequiredArgsConstructor;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * One dedicated Lettuce connection pool for the whole service, sized from
 * {@code arceus.cache.pool.*}. Two templates are exposed on top of it:
 * <ul>
 *   <li>{@code cacheRedisTemplate} - String keys / raw byte[] values, used
 *       by {@link com.arijit.cacheservice.service.CacheStoreService} for
 *       every String/Hash/List/Set/SortedSet operation, so serialization
 *       and compression stay entirely in this service's control rather
 *       than Spring's default JDK serialization.</li>
 *   <li>{@code cacheStringRedisTemplate} - plain String/String, used for
 *       SCAN-based eviction and the small stats counters where byte[]
 *       plumbing would just be overhead.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final CacheProperties cacheProperties;

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration(host, port);
        if (password != null && !password.isBlank()) {
            standaloneConfig.setPassword(password);
        }

        CacheProperties.Pool pool = cacheProperties.pool();
        GenericObjectPoolConfig<io.lettuce.core.api.StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(pool.maxActive());
        poolConfig.setMaxIdle(pool.maxIdle());
        poolConfig.setMinIdle(pool.minIdle());
        poolConfig.setMaxWait(Duration.ofMillis(pool.maxWaitMillis()));

        LettuceClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(5))
                .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    public RedisTemplate<String, byte[]> cacheRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate cacheStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
