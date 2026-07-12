package com.arijit.gatewayservice.featureflag;

import com.arijit.gatewayservice.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Fourth stage of the pipeline (after JWT auth, before rate limiting):
 * per-route feature-flag kill-switch - see RouteFlagGatewayClient's
 * Javadoc. Runs before rate limiting deliberately, so a disabled route
 * fails fast without spending a rate-limit request budget slot on
 * something that was never going to be forwarded anyway.
 */
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
            errorResponseWriter.write(request, response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "Service Unavailable", "This route is temporarily disabled", Map.of());
            return;
        }

        chain.doFilter(request, response);
    }

    /** Mirrors ProxyController.Route's prefixes - kept independent since this runs before the proxy layer even sees the request. */
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
