package com.arijit.authservice.security;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.otp.OAuth2ExchangeService;
import com.arijit.authservice.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * This is the bridge between "Google/GitHub says this person is legit" and
 * "our system issues its own JWT for them".
 *
 * Spring Security's OAuth2 login flow calls this automatically after the
 * user approves access on Google's/GitHub's consent screen. At that point
 * we have an OAuth2User with whatever profile fields that provider exposes -
 * our job is to turn that into one of OUR User rows.
 *
 * We deliberately do NOT put the access/refresh tokens in this redirect:
 * it's a browser GET request, so anything in the URL lands in browser
 * history, the server's access logs, and any Referer header sent
 * afterwards. Instead we hand back a random single-use exchange code (see
 * {@link OAuth2ExchangeService}) and let the frontend POST it to
 * {@code /api/auth/oauth2/exchange} to get the real tokens in a response
 * body instead.
 */
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
        String registrationId = oauthToken.getAuthorizedClientRegistrationId(); // "google" or "github"
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        User user = resolveUser(registrationId, oAuth2User);
        String code = oAuth2ExchangeService.issueCode(user.getId());

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
            // GitHub only includes "email" in the profile response if the
            // user has made an email public; fall back to a stable
            // placeholder tied to their username so we always have something unique.
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
            // Google's OpenID Connect fields: "sub" is the stable user id,
            // never reuse "email" as the identity key - people can change it.
            providerId = oAuth2User.getAttribute("sub");
            email = oAuth2User.getAttribute("email");
            name = oAuth2User.getAttribute("name");
        }

        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> linkProviderToExistingAccount(existing, provider, providerId))
                        .orElseGet(() -> userRepository.save(User.builder()
                                .name(name)
                                .email(email)
                                .provider(provider)
                                .providerId(providerId)
                                .emailVerified(true) // the provider already vouched for this
                                .build())));
    }

    private User linkProviderToExistingAccount(User existing, AuthProvider provider, String providerId) {
        existing.setProvider(provider);
        existing.setProviderId(providerId);
        return userRepository.save(existing);
    }
}
