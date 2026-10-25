package com.arijit.authservice.security;

import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.ApiKeyType;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock ApiKeyService apiKeyService;
    @Mock CustomUserDetailsService userDetailsService;
    @Mock FilterChain chain;

    private ApiKeyAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private User owner;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(apiKeyService, userDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        owner = User.builder().id(UUID.randomUUID()).email("a@b.com").name("A").build();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private UserDetails details(boolean enabled) {
        return org.springframework.security.core.userdetails.User.builder()
                .username("a@b.com").password("x").authorities("ROLE_USER").disabled(!enabled).build();
    }

    @Test
    void noHeaderPassesThrough() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void blankHeaderIgnored() throws Exception {
        request.addHeader("X-API-Key", "  ");

        filter.doFilter(request, response, chain);

        verifyNoInteractions(apiKeyService);
        verify(chain).doFilter(request, response);
    }

    @Test
    void validPersonalKeyAuthenticatesWithoutProjectAttributes() throws Exception {
        request.addHeader("X-API-Key", "ak_good");
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).user(owner).keyPrefix("ak_good0").build();
        when(apiKeyService.validateAndTouch("ak_good")).thenReturn(Optional.of(key));
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(details(true));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_PROJECT_ID)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void projectKeyExposesTenantAttributes() throws Exception {
        UUID org = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        request.addHeader("X-API-Key", "sk_proj");
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).user(owner).keyPrefix("sk_proj0")
                .organizationId(org).projectId(project).environment("production")
                .keyType(ApiKeyType.SECRET).scopes("read,write").build();
        when(apiKeyService.validateAndTouch("sk_proj")).thenReturn(Optional.of(key));
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(details(true));

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_ORGANIZATION_ID)).isEqualTo(org.toString());
        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_PROJECT_ID)).isEqualTo(project.toString());
        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_ENVIRONMENT)).isEqualTo("production");
        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_KEY_TYPE)).isEqualTo("SECRET");
        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_SCOPES)).isEqualTo("read,write");
    }

    @Test
    void projectKeyWithoutScopesOmitsScopeAttribute() throws Exception {
        request.addHeader("X-API-Key", "pk_proj");
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).user(owner).keyPrefix("pk_proj0")
                .organizationId(UUID.randomUUID()).projectId(UUID.randomUUID()).environment("dev")
                .keyType(ApiKeyType.PUBLIC).build();
        when(apiKeyService.validateAndTouch("pk_proj")).thenReturn(Optional.of(key));
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(details(true));

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_SCOPES)).isNull();
        assertThat(request.getAttribute(ApiKeyAuthFilter.ATTR_KEY_TYPE)).isEqualTo("PUBLIC");
    }

    @Test
    void unknownOrRevokedKeyDoesNotAuthenticate() throws Exception {
        request.addHeader("X-API-Key", "ak_bad");
        when(apiKeyService.validateAndTouch("ak_bad")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void disabledOwnerIsNotAuthenticated() throws Exception {
        request.addHeader("X-API-Key", "ak_good");
        ApiKey key = ApiKey.builder().id(UUID.randomUUID()).user(owner).build();
        when(apiKeyService.validateAndTouch("ak_good")).thenReturn(Optional.of(key));
        when(userDetailsService.loadUserByUsername("a@b.com")).thenReturn(details(false));

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void existingAuthenticationWins() throws Exception {
        var existing = new UsernamePasswordAuthenticationToken("jwt-user", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(existing);
        request.addHeader("X-API-Key", "ak_good");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verifyNoInteractions(apiKeyService);
    }
}
