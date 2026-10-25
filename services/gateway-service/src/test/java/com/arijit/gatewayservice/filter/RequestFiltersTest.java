package com.arijit.gatewayservice.filter;

import com.arijit.gatewayservice.security.JwtAuthFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestFiltersTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void requestIdHonoursInboundHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> mdc = new AtomicReference<>();

        new RequestIdFilter().doFilter(req, res, (r, s) -> mdc.set(MDC.get("correlationId")));

        assertThat(req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("abc");
        assertThat(res.getHeader("X-Request-Id")).isEqualTo("abc");
        assertThat(mdc.get()).isEqualTo("abc");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void requestIdGeneratedWhenBlank() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", " ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        new RequestIdFilter().doFilter(req, res, (r, s) -> { });

        assertThat(UUID.fromString((String) req.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE))).isNotNull();
    }

    @Test
    void requestIdClearedEvenOnFailure() {
        FilterChain boom = (r, s) -> { throw new java.io.IOException("x"); };

        assertThatThrownBy(() -> new RequestIdFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), boom))
                .isInstanceOf(java.io.IOException.class);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void loggingFilterPassesThroughAndRethrows() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.setAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE, "a@b.com");
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        new RequestLoggingFilter().doFilter(req, new MockHttpServletResponse(), (r, s) -> called.set(true));
        assertThat(called.get()).isTrue();

        FilterChain boom = (r, s) -> { throw new java.io.IOException("x"); };
        assertThatThrownBy(() -> new RequestLoggingFilter().doFilter(req, new MockHttpServletResponse(), boom))
                .isInstanceOf(java.io.IOException.class);
    }
}
