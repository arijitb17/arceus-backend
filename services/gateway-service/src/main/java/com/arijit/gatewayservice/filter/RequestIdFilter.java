package com.arijit.gatewayservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * First stage of the pipeline. Honors an inbound X-Request-Id (useful when
 * a client or upstream load balancer already assigns one) or generates a
 * fresh UUID otherwise. Stashed as a request attribute so RequestLoggingFilter's
 * one structured access-log line can quote it, put into MDC so it also
 * shows up in every other log line for this request (see application.yml's
 * logging.pattern.level), and echoed back as a response header so the
 * client can quote it when reporting an issue. This is also the id that
 * gets forwarded to downstream services (see AuthGatewayGrpcService et al.),
 * which is what makes correlation ids work end-to-end across the platform.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        MDC.put(MDC_KEY, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Tomcat reuses worker threads across requests - without
            // clearing this, the next unrelated request on the same
            // thread could inherit a stale correlation id.
            MDC.remove(MDC_KEY);
        }
    }
}
