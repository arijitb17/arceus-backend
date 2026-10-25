package com.arijit.ratelimitservice.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void propagatesIncomingId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "abc");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (r, s) -> seen.set(MDC.get("correlationId"));

        filter.doFilter(req, res, chain);

        assertThat(seen.get()).isEqualTo("abc");
        assertThat(res.getHeader("X-Request-Id")).isEqualTo("abc");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void generatesUuidWhenMissing() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), res, (r, s) -> { });

        assertThat(UUID.fromString(res.getHeader("X-Request-Id"))).isNotNull();
    }
}
