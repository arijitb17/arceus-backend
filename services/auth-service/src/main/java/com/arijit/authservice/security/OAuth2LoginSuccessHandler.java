package com.arijit.authservice.security;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.otp.OAuth2ExchangeService;
import com.arijit.authservice.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final UserRepository userRepository;
    private final OAuth2ExchangeService oAuth2ExchangeService;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        String registrationId = oauthToken.getAuthorizedClientRegistrationId();
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        User user = resolveUser(registrationId, oAuth2User);
        String code = oAuth2ExchangeService.issueCode(user.getId());
        log.info("OAuth2 login succeeded: provider={} userId={}", registrationId, user.getId());

        String target = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code)
                .build()
                .toUriString();

        response.sendRedirect(target);
    }

    private User resolveUser(String registrationId, OAuth2User oAuth2User) {
        AuthProvider provider = "github".equalsIgnoreCase(registrationId)
                ? AuthProvider.GITHUB
                : AuthProvider.GOOGLE;

        String providerId;
        String email;
        String name;

        if (provider == AuthProvider.GITHUB) {
            Object idAttr = oAuth2User.getAttributes().get("id");
            providerId = String.valueOf(idAttr);
            String login = (String) oAuth2User.getAttributes().get("login");
            email = oAuth2User.getAttribute("email") != null
                    ? oAuth2User.getAttribute("email")
                    : login + "@users.noreply.github.com";
            name = oAuth2User.getAttribute("name") != null
                    ? oAuth2User.getAttribute("name")
                    : login;
        } else {
            providerId = oAuth2User.getAttribute("sub");
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
        }

        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> linkProviderToExistingAccount(existing, provider, providerId))
                        .orElseGet(() -> {
                            User created = userRepository.save(User.builder()
                                    .name(name)
                                    .email(email)
                                    .provider(provider)
                                    .providerId(providerId)
                                    .emailVerified(true)
                                    .build());
                            log.info("Created account from {} login: userId={}", provider, created.getId());
                            return created;
                        }));
    }

    private User linkProviderToExistingAccount(User existing, AuthProvider provider, String providerId) {
        existing.setProvider(provider);
        existing.setProviderId(providerId);
        log.info("Linked {} identity to existing account: userId={}", provider, existing.getId());
        return userRepository.save(existing);
    }
}
