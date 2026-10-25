package com.arijit.gatewayservice.ratelimit;

import com.arijit.gatewayservice.config.GatewayProperties;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {
    private final GatewayProperties properties;
    private final RateLimitGatewayClient rateLimitGatewayClient;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (properties.isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        Object userEmail = request.getAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE);
        String identity = userEmail != null ? "user:" + userEmail : "ip:" + request.getRemoteAddr();

        RateLimitGatewayClient.RateLimitDecision decision = rateLimitGatewayClient.check(identity, path);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingRequests()));

        if (!decision.allowed()) {
            log.warn("Rate limit exceeded: identity={} path={} retryAfter={}", identity, path, decision.retryAfter());
            response.setHeader("Retry-After", String.valueOf(decision.retryAfter()));
            errorResponseWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    "Too Many Requests", "Rate limit exceeded, try again later",
                    Map.of(
                            "X-RateLimit-Remaining", String.valueOf(decision.remainingRequests()),
                            "Retry-After", String.valueOf(decision.retryAfter())
                    ));
            return;
        }

        chain.doFilter(request, response);
    }
}
