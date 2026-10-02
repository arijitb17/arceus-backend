package com.arijit.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit, rather than relying on Spring Boot's Jackson auto-configuration
 * to expose one - NotificationDispatchService needs an ObjectMapper bean to
 * serialize/deserialize the `variables` map to/from `variablesJson`.
 * <p>
 * Because Spring Boot backs off its own auto-configured ObjectMapper the
 * moment any bean of that type exists, this bean becomes the ONE used for
 * every REST response in this service too. WRITE_DATES_AS_TIMESTAMPS is
 * disabled explicitly below to match Spring Boot's own default - see
 * auth-service's identical JacksonConfig for the full explanation of what
 * broke without it (every Instant field, e.g. NotificationRecord's
 * sentAt/createdAt, silently serialized as a raw epoch-seconds number
 * instead of an ISO-8601 string).
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
