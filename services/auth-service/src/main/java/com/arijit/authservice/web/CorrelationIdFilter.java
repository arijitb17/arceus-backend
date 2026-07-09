package com.arijit.authservice.web;

import com.arijit.arceus.events.domain.CorrelationConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Runs first, on every request. Honors an inbound X-Request-Id (set by the
 * Gateway when a request comes through it) or generates a fresh one when
 * this service is called directly (e.g. the OAuth2 redirect flow, or local
 * dev/tests). Puts it in MDC so every log line for this request - including
 * ones written by Spring Security or an unhandled exception, not just our
 * own code - carries the same id (see application.yml's
 * logging.pattern.level), and echoes it back as a response header.
 * <p>
 * This is also what {@link com.arijit.authservice.messaging.DomainEventPublisher}
 * reads back out of MDC to stamp on every published event, which is how a
 * single request can be traced end-to-end through the audit trail.
 */
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
            // MDC is thread-bound, and Tomcat reuses worker threads across
            // requests - without clearing this, the next unrelated request
            // handled by the same thread could inherit a stale correlation id.
            MDC.remove(CorrelationConstants.MDC_KEY);
        }
    }
}
