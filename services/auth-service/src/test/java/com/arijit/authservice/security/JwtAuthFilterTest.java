package com.arijit.authservice.security;

import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock FilterChain chain;

    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final UserDetails userDetails =
            User.builder().username("a@b.com").password("x").authorities("ROLE_USER").build();

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthorizationHeaderPassesThroughUnauthenticated() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtService);
    }

    @Test
    void nonBearerSchemeIgnored() throws Exception {
        request.addHeader("Authorization", "Basic abc");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(jwtService);
    }

    @Test
    void validBearerTokenAuthenticates() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractUsername("tok")).thenReturn("a@b.com");
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("tok", userDetails)).thenReturn(true);

        filter.doFilter(request, response, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isSameAs(userDetails);
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER");
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidTokenLeavesContextEmptyButContinuesChain() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractUsername("tok")).thenReturn("a@b.com");
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid("tok", userDetails)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void jwtExceptionIsSwallowed() throws Exception {
        request.addHeader("Authorization", "Bearer garbage");
        when(jwtService.extractUsername("garbage")).thenThrow(new MalformedJwtException("bad"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void unknownUserIsSwallowed() throws Exception {
        request.addHeader("Authorization", "Bearer tok");
        when(jwtService.extractUsername("tok")).thenReturn("ghost@b.com");
        when(userDetailsService.loadUserByUsername("ghost@b.com")).thenThrow(new UsernameNotFoundException("no"));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void existingAuthenticationIsNotOverwritten() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("someone", null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("Authorization", "Bearer tok");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verifyNoInteractions(jwtService);
        verify(chain).doFilter(any(), any());
    }
}
