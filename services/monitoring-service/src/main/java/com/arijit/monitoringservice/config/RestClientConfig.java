package com.arijit.monitoringservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * One shared {@link RestClient} every collector (ServiceHealthCollector,
 * ServiceMetricsCollector) uses to poll every monitored service's Actuator
 * endpoints. A short, explicit connect/read timeout is the whole point -
 * a monitored service that's hung (not just down) must never be allowed to
 * stall the poller past one polling interval, or one bad service would
 * starve every other service's health/metric collection too.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final MonitoringProperties properties;

    @Bean
    public RestClient monitoringRestClient() {
        long timeoutMs = properties.polling() != null ? properties.polling().httpTimeoutMs() : 2000L;
        Duration timeout = Duration.ofMillis(timeoutMs);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
