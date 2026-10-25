package com.arijit.authservice.config;

import com.arijit.authservice.web.CorrelationIdFilter;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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
