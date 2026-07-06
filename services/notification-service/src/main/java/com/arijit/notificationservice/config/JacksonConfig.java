package com.arijit.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit, rather than relying on Spring Boot's Jackson auto-configuration
 * to expose one - NotificationDispatchService needs an ObjectMapper bean to
 * serialize/deserialize the `variables` map to/from `variablesJson`.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
