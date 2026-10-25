package com.arijit.schedulerservice.service;

import com.arijit.schedulerservice.config.SchedulerProperties;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.exception.GlobalExceptionHandler;
import com.arijit.schedulerservice.security.InternalTokenAuthFilter;
import com.arijit.schedulerservice.security.RoleHeaderAuthFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerSecurityAndErrorsTest {

    private final FilterChain noop = (r, s) -> { };
    private final SchedulerProperties props = new SchedulerProperties(null, null, null, null, null, new SchedulerProperties.Internal("secret"));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalTokenAuthenticatesAsInternalRole() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Token", "secret");

        new InternalTokenAuthFilter(props).doFilter(req, new MockHttpServletResponse(), noop);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("internal-service");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_INTERNAL");
    }

    @Test
    void wrongTokenStaysAnonymous() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Token", "guess");

        new InternalTokenAuthFilter(props).doFilter(req, new MockHttpServletResponse(), noop);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void roleHeaderFilterBuildsAuthenticationFromGatewayHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "a@b.com");
        req.addHeader("X-User-Roles", "ROLE_ADMIN");

        new RoleHeaderAuthFilter().doFilter(req, new MockHttpServletResponse(), noop);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_ADMIN");
    }

    @Test
    void exceptionHandlerMapsKnownErrors() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertThat(handler.handleApiException(new ApiException("x", HttpStatus.NOT_FOUND)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleOptimisticLock(new OptimisticLockingFailureException("stale")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var generic = handler.handleGeneric(new RuntimeException("secret detail"));
        assertThat(generic.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(generic.getBody().get("message").toString()).doesNotContain("secret detail");
    }
}
