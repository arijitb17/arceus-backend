package com.arijit.featureflagservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Flag values of type JSON are arbitrary caller-supplied JSON (stored as
 * text, parsed only when handed back out in a FlagResponse) and
 * timestamps throughout this service are {@code java.time.Instant} - this
 * is the one ObjectMapper every controller/service/messaging/grpc class
 * shares so both are handled consistently everywhere.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
