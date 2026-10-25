package com.arijit.gatewayservice.security;

import com.arijit.gatewayservice.config.GatewayProperties;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtVerificationService jwt;
    @Mock FilterChain chain;

    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        GatewayProperties props = new GatewayProperties(new GatewayProperties.Jwt("s"),
                List.of("/api/auth/login", "/health"), new GatewayProperties.RateLimit(1, 1), new GatewayProperties.Cors(List.of()));
        filter = new JwtAuthFilter(props, jwt, new ErrorResponseWriter());
        request = new MockHttpServletRequest("GET", "/api/cache/x");
        request.setRequestURI("/api/cache/x");
        response = new MockHttpServletResponse();
    }

    @Test
    void publicPathSkipsAuthentication() throws Exception {
        request.setRequestURI("/api/auth/login");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwt);
    }

    @Test
    void alreadyAuthenticatedByApiKeyPassesThrough() throws Exception {
        request.setAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE, "svc@x.y");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwt);
    }

    @Test
    void missingHeaderIs401() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing or malformed Authorization header");
        verifyNoInteractions(chain);
    }

    @Test
    void nonBearerHeaderIs401() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void validTokenPopulatesAttributesAndContinues() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwt.verify("tok")).thenReturn(new JwtVerificationService.VerifiedToken("a@b.com", List.of("ROLE_USER")));

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE)).isEqualTo("a@b.com");
        assertThat(request.getAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE)).isEqualTo(List.of("ROLE_USER"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenIs401() throws Exception {
        request.addHeader("Authorization", "Bearer bad");
        when(jwt.verify("bad")).thenThrow(new JwtException("expired"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired access token");
        verifyNoInteractions(chain);
    }

    @Test
    void malformedTokenArgumentIs401() throws Exception {
        request.addHeader("Authorization", "Bearer ");
        when(jwt.verify("")).thenThrow(new IllegalArgumentException("empty"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void liveEndpointAcceptsAccessTokenQueryParam() throws Exception {
        request.setRequestURI("/api/live/events");
        request.setParameter("access_token", "qtok");
        when(jwt.verify("qtok")).thenReturn(new JwtVerificationService.VerifiedToken("a@b.com", List.of("ROLE_ADMIN")));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void queryParamTokenNotAcceptedElsewhere() throws Exception {
        request.setParameter("access_token", "qtok");

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(jwt);
    }
}
