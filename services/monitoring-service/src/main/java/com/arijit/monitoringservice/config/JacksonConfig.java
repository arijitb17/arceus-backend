package com.arijit.monitoringservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Every timestamp throughout this service is {@code java.time.Instant} -
 * this is the one ObjectMapper every controller/service/messaging/grpc
 * class shares so it's handled consistently everywhere, same convention as
 * feature-flag-service's JacksonConfig.
 * <p>
 * WRITE_DATES_AS_TIMESTAMPS must be disabled explicitly: a bare {@code new
 * ObjectMapper()} defaults it to true, which serializes every Instant as a
 * raw number of epoch *seconds* (Jackson's numeric Instant format) rather
 * than an ISO-8601 string. A JS client doing {@code new Date(value)} on
 * that number reads it as epoch *milliseconds* instead, landing every
 * timestamp about 20 days after 1970 - exactly the "Jan 1970" dates seen
 * on the dashboard. Spring Boot's auto-configured mapper disables this by
 * default; because this bean is @Primary and replaces that mapper
 * entirely, it has to set the same flag itself.
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
