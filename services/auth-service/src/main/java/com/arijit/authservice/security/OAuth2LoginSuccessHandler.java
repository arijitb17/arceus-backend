package com.arijit.authservice.security;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.repository.RefreshTokenRepository;
import com.arijit.authservice.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * This is the bridge between "Google/GitHub says this person is legit" and
 * "our system issues its own JWT for them".
 *
 * Spring Security's OAuth2 login flow calls this automatically after the
 * user approves access on Google's/GitHub's consent screen. At that point
 * we have an OAuth2User with whatever profile fields that provider exposes -
 * our job is to turn that into one of OUR User rows, then hand back the
 * exact same kind of JWT a local email/password login would produce.
 *
 * Why bother, instead of just using Google's token directly? Because every
 * other service in the platform (API Gateway, etc.) should only ever need
 * to understand ONE token format, regardless of how the person logged in.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService customUserDetailsService;

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

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, Map.of("name", user.getName()));
        String refreshTokenValue = jwtService.generateRefreshToken(userDetails);

        refreshTokenRepository.save(RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build());

        // Redirect the browser back to the frontend with both tokens attached.
        // The frontend is responsible for grabbing these from the URL and
        // storing them (e.g. in memory / secure storage) just like it would
        // after a normal login API call.
        String target = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshTokenValue)
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
