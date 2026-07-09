package com.arijit.authservice.config;

import com.arijit.authservice.security.ApiKeyAuthFilter;
import com.arijit.authservice.security.CustomUserDetailsService;
import com.arijit.authservice.security.JwtService;
import com.arijit.authservice.service.ApiKeyService;
import jakarta.servlet.http.HttpServletResponse;
import com.arijit.authservice.security.JwtAuthFilter;
import com.arijit.authservice.security.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

// This is the class that wires everything else in `security/` together into
// an actual set of rules Spring Security enforces on every request.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final ApiKeyService apiKeyService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    // Comma-separated list of exact origins allowed to call this API with
    // credentials, e.g. "https://app.example.com,https://admin.example.com".
    // Defaults to nothing allowed rather than "*" - a wildcard origin
    // combined with allowCredentials(true) is a real vulnerability (any
    // site can ride the browser's cookies/auth), not just a lint warning.
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    // Endpoints anyone can hit without a token - makes sense, since you can't
    // have a JWT yet if you're the one trying to register/log in in the first place.
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/register",
            "/api/auth/verify-email",
            "/api/auth/verify-phone",
            "/api/auth/login",
            "/api/auth/verify-login-otp",
            "/api/auth/forgot-password",
            "/api/auth/verify-forgot-password-otp",
            "/api/auth/reset-password",
            "/api/auth/refresh",
            "/api/auth/oauth2/exchange",
            "/oauth2/**",
            "/login/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection matters for browser form/cookie-based sessions.
                // We're stateless + token-based, so there's no session to arceus - safe to disable.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Tells Spring Security "never create an HttpSession for this app" -
                // every request must carry its own proof of identity (the JWT).
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Without this, a request to a protected endpoint with no/bad
                // JWT gets redirected into the oauth2Login flow instead of a
                // clean 401 - fine for a browser-driven app, wrong for an API.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.getWriter().write(
                                    "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Missing or invalid token\"}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )
                // Enables the /oauth2/authorization/google and /oauth2/authorization/github
                // endpoints, and routes successful logins through our custom handler.
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2LoginSuccessHandler)
                )
                .authenticationProvider(authenticationProvider())
                // Run our JWT check BEFORE Spring's own username/password filter,
                // so a valid Bearer token authenticates the request first.
                // Constructed here (not injected as a bean) so it's only
                // ever part of this one filter chain - see JwtAuthFilter's
                // own javadoc for why it deliberately isn't a @Component.
                .addFilterBefore(new JwtAuthFilter(jwtService, userDetailsService), UsernamePasswordAuthenticationFilter.class)
                // Alternate auth path via X-API-Key, for programmatic/service
                // callers - runs after JwtAuthFilter (each checks for an
                // authentication already present before doing anything, so
                // whichever credential is actually supplied wins).
                .addFilterBefore(new ApiKeyAuthFilter(apiKeyService, userDetailsService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt: a one-way hash designed to be slow on purpose, so brute-forcing
        // a stolen password database takes years instead of seconds.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // A wildcard origin combined with allowCredentials(true) lets ANY
        // website ride a logged-in user's bearer token via a browser
        // request - set app.cors.allowed-origins explicitly per
        // environment (e.g. https://app.example.com). Nothing is allowed
        // by default until it's configured.
        List<String> origins = allowedOrigins.isBlank()
                ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
