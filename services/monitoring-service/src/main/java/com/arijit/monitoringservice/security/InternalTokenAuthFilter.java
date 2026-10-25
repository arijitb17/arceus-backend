package com.arijit.monitoringservice.security;

import com.arijit.monitoringservice.config.MonitoringProperties;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class InternalTokenAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Token";

    private final String expectedToken;

    public InternalTokenAuthFilter(MonitoringProperties properties) {
        this.expectedToken = properties.internal().apiToken();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && !presented.isBlank() && presented.equals(expectedToken)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            var authToken = new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        if (presented != null && !presented.isBlank() && !presented.equals(expectedToken)) {
            log.warn("Rejected internal token on {} {}: token mismatch", request.getMethod(), request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }
}
