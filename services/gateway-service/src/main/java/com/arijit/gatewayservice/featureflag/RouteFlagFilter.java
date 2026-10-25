package com.arijit.gatewayservice.featureflag;

import com.arijit.gatewayservice.error.ErrorResponseWriter;
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

/**
 * Runs before rate limiting deliberately, so a disabled route fails fast without spending a rate-
 * limit request budget slot on something that was never going to be forwarded anyway.
 */
@Slf4j
@RequiredArgsConstructor
public class RouteFlagFilter extends OncePerRequestFilter {
    private final RouteFlagGatewayClient routeFlagGatewayClient;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String routeName = resolveRouteName(path);

        if (routeName != null && !routeFlagGatewayClient.isRouteEnabled(routeName)) {
            log.warn("Route '{}' is disabled via feature flag, rejecting {} {}", routeName, request.getMethod(), path);
            errorResponseWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Service Unavailable", "This route is temporarily disabled", Map.of());
            return;
        }

        chain.doFilter(request, response);
    }

    private String resolveRouteName(String path) {
        if (path.startsWith("/api/auth")) return "auth";
        if (path.startsWith("/api/notifications")) return "notifications";
        if (path.startsWith("/api/audit")) return "audit";
        if (path.startsWith("/api/monitoring")) return "monitoring";
        if (path.startsWith("/api/deployments")) return "deployments";
        if (path.startsWith("/api/scheduler")) return "scheduler";
        if (path.startsWith("/api/feature-flags")) return "feature-flags";
        if (path.startsWith("/api/cache")) return "cache";
        return null;
    }
}
