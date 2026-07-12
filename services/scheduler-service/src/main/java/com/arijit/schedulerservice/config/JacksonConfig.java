package com.arijit.schedulerservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Job payloads are arbitrary caller-supplied JSON (stored as text, parsed
 * only when handed to an HTTP callback or a RabbitMQ message body) and
 * timestamps throughout this service are {@code java.time.Instant} - this
 * is the one ObjectMapper every controller/service/messaging class shares
 * so both are handled consistently everywhere.
 * <p>
 * WRITE_DATES_AS_TIMESTAMPS must be disabled explicitly - see
 * monitoring-service's JacksonConfig for why a bare {@code new
 * ObjectMapper()} silently serializes Instant as epoch *seconds*, which a
 * JS client reading it as epoch *milliseconds* turns into a date ~20 days
 * after 1970.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .modulesToInstall(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
