package com.arijit.monitoringservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Every timestamp throughout this service is {@code java.time.Instant} -
 * this is the one ObjectMapper every controller/service/messaging/grpc
 * class shares so it's handled consistently everywhere, same convention as
 * feature-flag-service's JacksonConfig.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
