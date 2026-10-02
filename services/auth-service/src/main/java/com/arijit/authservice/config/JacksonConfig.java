package com.arijit.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit, rather than relying on Spring Boot's Jackson auto-configuration
 * to expose one - DomainEventPublisher needs an ObjectMapper bean to turn
 * each DomainEvent into the Map<String,Object> payload on EventEnvelope.
 * <p>
 * Because Spring Boot backs off its own auto-configured ObjectMapper the
 * moment any bean of that type exists, this bean becomes the ONE used
 * for every REST response in this service too - not just the event
 * payload conversion it was written for. That's why
 * WRITE_DATES_AS_TIMESTAMPS is disabled explicitly below: a bare
 * `new ObjectMapper()` defaults it to true (Instant serializes as a raw
 * epoch-seconds number, e.g. "1786576690.776551100"), whereas Spring
 * Boot's own auto-configured mapper always disables it (Instant
 * serializes as an ISO-8601 string). Every response DTO with an Instant
 * field - createdAt, lastUsedAt, and so on - was silently serializing as
 * that raw number instead, which every frontend `new Date(value)` call
 * then misread as milliseconds instead of seconds, showing dates from
 * January 1970 instead of the real ones.
 *
 * findAndRegisterModules() picks up jackson-datatype-jsr310 (for
 * java.time.Instant) automatically if it's on the classpath, the same way
 * Spring Boot's own auto-configured mapper would.
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
