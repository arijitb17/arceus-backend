package com.arijit.schedulerservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * One shared, timeout-bounded RestClient for every HTTP_CALLBACK job
 * target. Timeouts are deliberately short and configurable - a slow or
 * hanging callback target must not be able to tie up a worker thread
 * indefinitely (see AsyncConfig's bounded pool). Built directly on
 * {@link JdkClientHttpRequestFactory}/{@code java.net.http.HttpClient}
 * rather than Boot's client-factory builder helpers, since the latter's
 * package has moved between minor Boot versions - this way stays stable
 * regardless.
 */
@Configuration
@RequiredArgsConstructor
public class RestClientConfig {

    private final SchedulerProperties properties;

    @Bean
    public RestClient jobCallbackRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.callback().connectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.callback().readTimeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }
}
