package com.arijit.authservice.config;

import com.arijit.authservice.web.CorrelationIdFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * CorrelationIdFilter needs to run before Spring Security's own filter
 * chain (so a correlation id already exists in MDC by the time
 * JwtAuthFilter or any authentication-failure logging runs), which means
 * it can't just be a plain {@code @Component} - Spring Security's chain is
 * itself registered as a servlet filter at a very early order. Registering
 * it explicitly with {@link Ordered#HIGHEST_PRECEDENCE} guarantees it runs
 * first regardless of Spring Security's own registration order.
 */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<Filter> correlationIdFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>(new CorrelationIdFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
