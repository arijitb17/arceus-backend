package com.arijit.cacheservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Every serializer, {@code CacheStoreService}, and {@code CacheGrpcServiceImpl}
 * injects {@link ObjectMapper} directly, so it's defined explicitly here
 * rather than relying on Spring Boot's Jackson autoconfiguration - which
 * this module's slimmed-down starter set doesn't reliably trigger.
 * Registers {@link JavaTimeModule} so {@code Instant} fields on
 * {@code CacheResponse} serialize as ISO-8601 strings instead of failing
 * or falling back to numeric timestamps.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
