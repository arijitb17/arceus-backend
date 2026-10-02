package com.arijit.organizationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Because Spring Boot backs off its own auto-configured ObjectMapper the
 * moment any bean of that type exists, this bean becomes the ONE used for
 * every REST response in this service. WRITE_DATES_AS_TIMESTAMPS is
 * disabled explicitly below to match Spring Boot's own default - see
 * auth-service's identical JacksonConfig for the full explanation of what
 * broke without it (every Instant field - Organization/Project/
 * ModuleEnablement/Secret createdAt, and so on - silently serialized as a
 * raw epoch-seconds number instead of an ISO-8601 string).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
