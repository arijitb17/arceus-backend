package com.arijit.gatewayservice.config;

import com.arijit.gatewayservice.error.ErrorResponseWriter;
import com.arijit.gatewayservice.featureflag.RouteFlagFilter;
import com.arijit.gatewayservice.featureflag.RouteFlagGatewayClient;
import com.arijit.gatewayservice.filter.RequestIdFilter;
import com.arijit.gatewayservice.filter.RequestLoggingFilter;
import com.arijit.gatewayservice.ratelimit.RateLimitFilter;
import com.arijit.gatewayservice.ratelimit.RateLimitGatewayClient;
import com.arijit.gatewayservice.security.ApiKeyAuthFilter;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import com.arijit.gatewayservice.security.JwtVerificationService;
import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<Filter> corsFilter(GatewayProperties properties) {
        return registration(new CorsFilter(corsConfigurationSource(properties)), 0);
    }

    @Bean
    public FilterRegistrationBean<Filter> requestIdFilter() {
        return registration(new RequestIdFilter(), 1);
    }

    @Bean
    public FilterRegistrationBean<Filter> requestLoggingFilter() {
        return registration(new RequestLoggingFilter(), 2);
    }

    @Bean
    public FilterRegistrationBean<Filter> apiKeyAuthFilter(
            ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceBlockingStub apiKeyValidationStub,
            ErrorResponseWriter errorResponseWriter) {
        return registration(new ApiKeyAuthFilter(apiKeyValidationStub, errorResponseWriter), 3);
    }

    @Bean
    public FilterRegistrationBean<Filter> jwtAuthFilter(GatewayProperties properties,
                                                         JwtVerificationService jwtVerificationService,
                                                         ErrorResponseWriter errorResponseWriter) {
        return registration(new JwtAuthFilter(properties, jwtVerificationService, errorResponseWriter), 4);
    }

    @Bean
    public FilterRegistrationBean<Filter> routeFlagFilter(RouteFlagGatewayClient routeFlagGatewayClient,
                                                           ErrorResponseWriter errorResponseWriter) {
        return registration(new RouteFlagFilter(routeFlagGatewayClient, errorResponseWriter), 5);
    }

    @Bean
    public FilterRegistrationBean<Filter> rateLimitFilter(GatewayProperties properties,
                                                           RateLimitGatewayClient rateLimitGatewayClient,
                                                           ErrorResponseWriter errorResponseWriter) {
        return registration(new RateLimitFilter(properties, rateLimitGatewayClient, errorResponseWriter), 6);
    }

    private CorsConfigurationSource corsConfigurationSource(GatewayProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        // No origins allowed until arceus.gateway.cors.allowed-origins is configured - never fall
        // back to "*" here, since allowCredentials is true (the Authorization header rides along
        // with every call).
        configuration.setAllowedOrigins(properties.cors() == null ? List.of() : properties.cors().originsOrEmpty());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "X-API-Key", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Remaining", "Retry-After"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private FilterRegistrationBean<Filter> registration(Filter filter, int order) {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(order);
        return bean;
    }
}
