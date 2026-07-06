package com.arijit.auditservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit, rather than relying on Spring Boot's Jackson auto-configuration
 * to expose one - DomainEventAuditConsumer needs an ObjectMapper bean to
 * serialize the event payload into AuditLog.metadataJson.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
