package com.arijit.authservice.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// This runs on EVERY incoming request, once (that's what "OncePerRequestFilter"
// guarantees). Its job: if there's a "Authorization: Bearer <token>" header,
// validate it and tell Spring Security "this request is authenticated as X"
// so downstream @PreAuthorize checks / SecurityContext lookups work.
//
// If there's no header, or the token's bad, we just let the request continue
// unauthenticated - it's up to SecurityConfig's authorizeHttpRequests rules
// to decide whether that's allowed for this particular endpoint.
//
// NOT a @Component on purpose: SecurityConfig instantiates this directly and
// adds it into the Spring Security filter chain via addFilterBefore(). If it
// were also a @Component, Spring Boot's servlet-filter auto-registration
// would ALSO register it as an independent global filter, running it twice
// on every request.
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7); // strip "Bearer "

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                final String userEmail = jwtService.extractUsername(jwt);
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
                // Expired, malformed, tampered, wrong-key, or a token type
                // that isn't actually an access token - any of these mean
                // "this request just isn't authenticated", not "server
                // error". Leaving the SecurityContext empty lets
                // SecurityConfig's authorizeHttpRequests rules produce a
                // clean 401 for protected endpoints, exactly as if no
                // header had been sent at all.
                log.debug("Rejecting invalid access token: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
