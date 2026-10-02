package com.arijit.gatewayservice.security;

import com.arijit.gatewayservice.config.GatewayProperties;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Second stage of the pipeline (after request-id/logging, before rate
 * limiting): rejects requests to protected routes that don't carry a
 * valid, unexpired access token, and stamps the authenticated user's
 * email onto the request as an attribute for downstream filters/
 * controllers to use. Public routes (login, register, refresh, /health,
 * ...) pass straight through untouched.
 */
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
            // Already authenticated by ApiKeyAuthFilter (runs immediately
            // before this filter - see FilterConfig's ordering and that
            // filter's javadoc). A request can't present both an API key
            // and a JWT in any flow this platform has, but if it somehow
            // did, the API key - having already been verified against
            // auth-service - wins rather than being silently overwritten.
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        String token;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring("Bearer ".length());
        } else if (path.startsWith("/api/live/") && request.getParameter("access_token") != null) {
            // Browsers' EventSource API can't set custom request headers, so
            // there's no way for an SSE client to send a Bearer header - the
            // token has to ride in the URL instead. Scoped to this one
            // route on purpose: nowhere else accepts a token this way, and
            // request.getRequestURI() (what RequestLoggingFilter logs)
            // never includes the query string, so this doesn't end up in
            // the Gateway's own access log.
            token = request.getParameter("access_token");
        } else {
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Missing or malformed Authorization header");
            return;
        }
        try {
            JwtVerificationService.VerifiedToken verified = jwtVerificationService.verify(token);
            request.setAttribute(USER_EMAIL_ATTRIBUTE, verified.subject());
            request.setAttribute(USER_ROLES_ATTRIBUTE, verified.roles());
        } catch (JwtException | IllegalArgumentException ex) {
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Invalid or expired access token");
            return;
        }

        chain.doFilter(request, response);
    }
}
