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

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            errorResponseWriter.write(request, response, HttpStatus.UNAUTHORIZED.value(),
                    "Unauthorized", "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring("Bearer ".length());
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
