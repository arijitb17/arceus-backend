package com.arijit.featureflagservice.security;

import com.arijit.featureflagservice.FlagTestData;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlagSecurityFiltersTest {

    private final FilterChain noop = (r, s) -> { };

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalTokenGrantsInternalRole() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Token", "internal-token");

        new InternalTokenAuthFilter(FlagTestData.properties(true, 60, true)).doFilter(req, new MockHttpServletResponse(), noop);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo("internal-service");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_INTERNAL");
    }

    @Test
    void wrongOrMissingTokenLeavesRequestAnonymous() throws Exception {
        var filter = new InternalTokenAuthFilter(FlagTestData.properties(true, 60, true));
        MockHttpServletRequest wrong = new MockHttpServletRequest();
        wrong.addHeader("X-Internal-Token", "nope");

        filter.doFilter(wrong, new MockHttpServletResponse(), noop);
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), noop);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void internalTokenDoesNotReplaceExistingAuthentication() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("admin", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Internal-Token", "internal-token");

        new InternalTokenAuthFilter(FlagTestData.properties(true, 60, true)).doFilter(req, new MockHttpServletResponse(), noop);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    @Test
    void roleHeaderFilterBuildsAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Email", "a@b.com");
        req.addHeader("X-User-Roles", "ROLE_ADMIN,ROLE_USER");

        new RoleHeaderAuthFilter().doFilter(req, new MockHttpServletResponse(), noop);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("a@b.com");
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void roleHeaderFilterIgnoresRequestsWithoutEmail() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Roles", "ROLE_ADMIN");

        new RoleHeaderAuthFilter().doFilter(req, new MockHttpServletResponse(), noop);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
