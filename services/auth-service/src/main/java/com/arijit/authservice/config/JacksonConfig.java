package com.arijit.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit, rather than relying on Spring Boot's Jackson auto-configuration
 * to expose one - DomainEventPublisher needs an ObjectMapper bean to turn
 * each DomainEvent into the Map<String,Object> payload on EventEnvelope.
 *
 * findAndRegisterModules() picks up jackson-datatype-jsr310 (for
 * java.time.Instant) automatically if it's on the classpath, the same way
 * Spring Boot's own auto-configured mapper would.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
