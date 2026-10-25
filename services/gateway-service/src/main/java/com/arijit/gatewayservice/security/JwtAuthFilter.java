package com.arijit.gatewayservice.security;

import com.arijit.gatewayservice.config.GatewayProperties;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    public static final String USER_EMAIL_ATTRIBUTE = "authenticatedUserEmail";
    public static final String USER_ROLES_ATTRIBUTE = "authenticatedUserRoles";

    private final GatewayProperties properties;
    private final JwtVerificationService jwtVerificationService;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (properties.isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        if (request.getAttribute(USER_EMAIL_ATTRIBUTE) != null) {
            // Already authenticated by ApiKeyAuthFilter (runs immediately before this filter - see
            // FilterConfig's ordering and that filter's javadoc). A request can't present both an
            // API key and a JWT in any flow this platform has, but if it somehow did, the API key -
            // having already been verified against auth-service - wins rather than being silently
            // overwritten.
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length());
        } else if (path.startsWith("/api/live/") && request.getParameter("access_token") != null) {
            // Scoped to this one route on purpose: nowhere else accepts a token this way, and
            // request.getRequestURI() (what RequestLoggingFilter logs) never includes the query
            // string, so this doesn't end up in the Gateway's own access log.
            token = request.getParameter("access_token");
        } else {
            log.debug("Rejected {} {}: missing or malformed Authorization header", request.getMethod(), path);
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Missing or malformed Authorization header");
            return;
        }
        try {
            JwtVerificationService.VerifiedToken verified = jwtVerificationService.verify(token);
            request.setAttribute(USER_EMAIL_ATTRIBUTE, verified.subject());
            request.setAttribute(USER_ROLES_ATTRIBUTE, verified.roles());
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("Rejected {} {}: invalid access token ({})", request.getMethod(), path, ex.getClass().getSimpleName());
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Invalid or expired access token");
            return;
        }

        chain.doFilter(request, response);
    }
}
