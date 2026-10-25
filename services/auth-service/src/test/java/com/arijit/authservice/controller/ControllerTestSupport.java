package com.arijit.authservice.controller;

import com.arijit.authservice.exception.GlobalExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Builds a standalone MockMvc (no Spring context) wired with the real exception handler and @AuthenticationPrincipal support. */
final class ControllerTestSupport {

    private ControllerTestSupport() {}

    static MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    static void authenticateAs(String email) {
        UserDetails details = User.builder().username(email).password("x").authorities("ROLE_USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    static void clear() {
        SecurityContextHolder.clearContext();
    }
}
