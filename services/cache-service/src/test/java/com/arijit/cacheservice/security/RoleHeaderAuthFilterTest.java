package com.arijit.cacheservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RoleHeaderAuthFilterTest {

    private final RoleHeaderAuthFilter filter = new RoleHeaderAuthFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private AtomicBoolean run(MockHttpServletRequest req) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        FilterChain chain = (r, s) -> called.set(true);
        filter.doFilter(req, new MockHttpServletResponse(), chain);
        return called;
    }

    @Test
    void buildsAuthenticationFromHeaders() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "a@b.com");
        req.addHeader("X-User-Roles", "ROLE_USER, ROLE_ADMIN ,");

        assertThat(run(req)).isTrue();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("a@b.com");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void emailWithoutRolesGetsNoAuthorities() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "a@b.com");

        run(req);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
    }

    @Test
    void noEmailMeansAnonymous() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Roles", "ROLE_ADMIN");

        assertThat(run(req)).isTrue();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void blankEmailIgnored() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "   ");

        run(req);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNotOverrideExistingAuthentication() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("keep", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "other@b.com");

        run(req);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
