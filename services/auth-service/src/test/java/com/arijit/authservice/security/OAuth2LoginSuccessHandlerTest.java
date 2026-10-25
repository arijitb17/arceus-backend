package com.arijit.authservice.security;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.otp.OAuth2ExchangeService;
import com.arijit.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock UserRepository userRepository;
    @Mock OAuth2ExchangeService exchangeService;

    private OAuth2LoginSuccessHandler handler;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginSuccessHandler(userRepository, exchangeService);
        ReflectionTestUtils.setField(handler, "redirectUri", "https://app.arceus.io/oauth/callback");
        response = new MockHttpServletResponse();
    }

    private OAuth2AuthenticationToken token(String registrationId, Map<String, Object> attrs, String nameKey) {
        OAuth2User principal = new DefaultOAuth2User(AuthorityUtils.createAuthorityList("ROLE_USER"), attrs, nameKey);
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), registrationId);
    }

    private void stubCode(UUID id) {
        when(exchangeService.issueCode(id)).thenReturn("CODE123");
    }

    @Test
    void existingProviderIdentityLogsInAndRedirectsWithCode() throws Exception {
        User existing = User.builder().id(UUID.randomUUID()).email("g@x.y").provider(AuthProvider.GOOGLE).build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-1")).thenReturn(Optional.of(existing));
        stubCode(existing.getId());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("google", Map.of("sub", "sub-1", "email", "g@x.y", "name", "G"), "sub"));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://app.arceus.io/oauth/callback?code=CODE123");
        verify(userRepository, never()).save(any());
    }

    @Test
    void newGoogleUserIsCreatedAndEmailMarkedVerified() throws Exception {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@x.y")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(exchangeService.issueCode(any())).thenReturn("CODE123");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("google", Map.of("sub", "sub-2", "email", "new@x.y", "name", "New"), "sub"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(cap.getValue().getProviderId()).isEqualTo("sub-2");
        assertThat(cap.getValue().getEmail()).isEqualTo("new@x.y");
        assertThat(cap.getValue().isEmailVerified()).isTrue();
        assertThat(response.getRedirectedUrl()).contains("code=CODE123");
    }

    @Test
    void existingLocalAccountWithSameEmailGetsProviderLinked() throws Exception {
        User local = User.builder().id(UUID.randomUUID()).email("same@x.y").provider(AuthProvider.LOCAL).build();
        when(userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "sub-3")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("same@x.y")).thenReturn(Optional.of(local));
        when(userRepository.save(local)).thenReturn(local);
        stubCode(local.getId());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("google", Map.of("sub", "sub-3", "email", "same@x.y", "name", "S"), "sub"));

        assertThat(local.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(local.getProviderId()).isEqualTo("sub-3");
    }

    @Test
    void githubUsesNumericIdAndNameAndEmailWhenPresent() throws Exception {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GITHUB, "4242")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("octo@gh.io")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(exchangeService.issueCode(any())).thenReturn("C");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("github", Map.of("id", 4242, "login", "octocat", "email", "octo@gh.io", "name", "Octo Cat"), "login"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getProvider()).isEqualTo(AuthProvider.GITHUB);
        assertThat(cap.getValue().getProviderId()).isEqualTo("4242");
        assertThat(cap.getValue().getName()).isEqualTo("Octo Cat");
    }

    @Test
    void githubWithPrivateEmailFallsBackToNoReplyAndLoginAsName() throws Exception {
        when(userRepository.findByProviderAndProviderId(AuthProvider.GITHUB, "7")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("octocat@users.noreply.github.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(exchangeService.issueCode(any())).thenReturn("C");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                token("github", Map.of("id", 7, "login", "octocat"), "login"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertThat(cap.getValue().getEmail()).isEqualTo("octocat@users.noreply.github.com");
        assertThat(cap.getValue().getName()).isEqualTo("octocat");
    }
}
