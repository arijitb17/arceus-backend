package com.arijit.organizationservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class RoleHeaderAuthFilter extends OncePerRequestFilter {
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String email = request.getHeader(HEADER_USER_EMAIL);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);

        if (email != null && !email.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {
            List<GrantedAuthority> authorities = (rolesHeader == null || rolesHeader.isBlank())
                    ? List.of()
                    : Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                        .toList();

            var authToken = new UsernamePasswordAuthenticationToken(email, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
