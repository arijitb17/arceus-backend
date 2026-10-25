package com.arijit.authservice.web;

import com.arijit.arceus.events.domain.CorrelationConstants;
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

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void reusesIncomingCorrelationIdAndExposesItDuringChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationConstants.HEADER_NAME, "abc-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (r, s) -> seen.set(MDC.get(CorrelationConstants.MDC_KEY));

        filter.doFilter(req, res, chain);

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(res.getHeader(CorrelationConstants.HEADER_NAME)).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull();
    }

    @Test
    void generatesIdWhenHeaderMissingOrBlank() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(CorrelationConstants.HEADER_NAME, "  ");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, s) -> { });

        assertThat(UUID.fromString(res.getHeader(CorrelationConstants.HEADER_NAME))).isNotNull();
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        FilterChain boom = (r, s) -> { throw new java.io.IOException("x"); };

        assertThatThrownBy(() -> filter.doFilter(req, new MockHttpServletResponse(), boom))
                .isInstanceOf(java.io.IOException.class);

        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull();
    }
}
