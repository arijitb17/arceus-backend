package com.arijit.gatewayservice.filter;

import com.arijit.gatewayservice.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Wraps the entire rest of the pipeline (JWT check, rate limiting, gRPC
 * forwarding) to time it end to end, then logs one structured line per
 * request. Runs after RequestIdFilter but before everything else, so the
 * elapsed time includes the downstream call and the user id - set later in
 * the chain by JwtAuthFilter as a request attribute - is still visible here
 * once chain.doFilter() returns, since attributes live on the shared
 * request object.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.arijit.gatewayservice.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = System.currentTimeMillis() - start;
            String requestId = String.valueOf(request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE));
            Object userEmail = request.getAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE);

            log.info("requestId={} user={} method={} path={} status={} tookMs={}",
                    requestId,
                    userEmail != null ? userEmail : "anonymous",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    tookMs);
        }
    }
}
