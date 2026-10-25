package com.arijit.arceus.ratelimit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void returnsMarkerWhenNoRequestContext() {
        assertThat(ClientIpResolver.resolve()).isEqualTo("no-request-context");
    }

    @Test
    void usesRemoteAddrWhenNoForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        bind(request);

        assertThat(ClientIpResolver.resolve()).isEqualTo("192.168.1.10");
    }

    @Test
    void usesFirstEntryOfForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.7 , 70.41.3.18, 150.172.238.178");
        bind(request);

        assertThat(ClientIpResolver.resolve()).isEqualTo("203.0.113.7");
    }

    @Test
    void ignoresBlankForwardedFor() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "   ");
        bind(request);

        assertThat(ClientIpResolver.resolve()).isEqualTo("10.0.0.2");
    }
}
