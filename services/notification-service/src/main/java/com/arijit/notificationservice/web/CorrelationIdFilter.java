package com.arijit.notificationservice.web;

import com.arijit.arceus.events.domain.CorrelationConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs first, on every request to this service's own REST endpoints
 * (health, notification history, etc). Honors an inbound X-Request-Id or
 * generates a fresh one, puts it in MDC so every log line for this request
 * carries it (see application.yml's logging.pattern.level), and echoes it
 * back as a response header. No Spring Security in this service, so a
 * plain {@code @Component} is safe here - nothing else registers this
 * filter a second time.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String correlationId = request.getHeader(CorrelationConstants.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CorrelationConstants.MDC_KEY, correlationId);
        response.setHeader(CorrelationConstants.HEADER_NAME, correlationId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationConstants.MDC_KEY);
        }
    }
}
