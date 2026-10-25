package com.arijit.authservice.service;

import com.arijit.arceus.events.domain.*;
import com.arijit.arceus.events.otp.OtpChannel;
import com.arijit.arceus.events.otp.OtpNotificationMessage;
import com.arijit.arceus.events.otp.OtpPurpose;
import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.featureflag.FeatureFlagGateService;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.messaging.OtpProducer;
import com.arijit.authservice.otp.*;
import com.arijit.authservice.repository.RefreshTokenRepository;
import com.arijit.authservice.repository.UserRepository;
import com.arijit.authservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock EmailOtpService emailOtpService;
    @Mock PhoneOtpVerifier phoneOtpVerifier;
    @Mock OtpProducer otpProducer;
    @Mock PasswordResetTokenService passwordResetTokenService;
    @Mock PendingChangeService pendingChangeService;
    @Mock DomainEventPublisher domainEventPublisher;
    @Mock OAuth2ExchangeService oAuth2ExchangeService;
    @Mock FeatureFlagGateService featureFlagGateService;
    @Mock UserDetails userDetails;

    @InjectMocks AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .name("Ash")
                .email("ash@pallet.town")
                .phoneNumber("+919876543210")
                .password("encoded")
                .build();
    }

    private static String sha256(String raw) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void stubTokenIssuance() {
        when(userDetailsService.loadUserByUsername(user.getEmail())).thenReturn(userDetails);
        when(jwtService.generateAccessToken(eq(userDetails), anyMap())).thenReturn("access-jwt");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenExpirationMs()).thenReturn(60_000L);
    }

    private void assertApiException(Runnable r, HttpStatus status, String messagePart) {
        assertThatThrownBy(r::run)
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(status);
                    assertThat(ex.getMessage()).contains(messagePart);
                });
    }

    @Nested
    class Register {
        private final RegisterRequest req = new RegisterRequest("Ash", "ash@pallet.town", "+919876543210", "pikachu123");

        @Test
        void createsUserPublishesEventAndSendsBothOtps() {
            when(userRepository.existsByEmail(req.email())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(req.phoneNumber())).thenReturn(false);
            when(passwordEncoder.encode("pikachu123")).thenReturn("hashed");
            when(emailOtpService.generateAndStore(req.email(), OtpPurpose.EMAIL_VERIFICATION)).thenReturn("123456");

            MessageResponse response = authService.register(req);

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
            assertThat(saved.getValue().getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(saved.getValue().isEmailVerified()).isFalse();
            assertThat(saved.getValue().isPhoneVerified()).isFalse();

            verify(featureFlagGateService).requireEnabled("auth.registration.enabled", "Registration");
            verify(domainEventPublisher).publish(any(UserRegisteredEvent.class));

            ArgumentCaptor<OtpNotificationMessage> msgs = ArgumentCaptor.forClass(OtpNotificationMessage.class);
            verify(otpProducer, times(2)).publish(msgs.capture());
            assertThat(msgs.getAllValues()).extracting(OtpNotificationMessage::channel)
                    .containsExactly(OtpChannel.EMAIL, OtpChannel.SMS);
            assertThat(msgs.getAllValues().get(0).otpCode()).isEqualTo("123456");
            assertThat(msgs.getAllValues().get(1).purpose()).isEqualTo(OtpPurpose.PHONE_VERIFICATION);
            assertThat(response.message()).contains("Account created");
        }

        @Test
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmail(req.email())).thenReturn(true);

            assertApiException(() -> authService.register(req), HttpStatus.CONFLICT, "email");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(otpProducer, domainEventPublisher);
        }

        @Test
        void rejectsDuplicatePhone() {
            when(userRepository.existsByEmail(req.email())).thenReturn(false);
            when(userRepository.existsByPhoneNumber(req.phoneNumber())).thenReturn(true);

            assertApiException(() -> authService.register(req), HttpStatus.CONFLICT, "phone");

            verify(userRepository, never()).save(any());
        }

        @Test
        void propagatesFeatureFlagDisabled() {
            doThrow(new ApiException("Registration is temporarily disabled", HttpStatus.SERVICE_UNAVAILABLE))
                    .when(featureFlagGateService).requireEnabled(anyString(), anyString());

            assertApiException(() -> authService.register(req), HttpStatus.SERVICE_UNAVAILABLE, "disabled");

            verifyNoInteractions(userRepository);
        }
    }

    @Nested
    class VerifyContact {
        @Test
        void verifyEmailMarksVerifiedAndReportsWaitingOnPhone() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(user.getEmail(), OtpPurpose.EMAIL_VERIFICATION, "111111")).thenReturn(true);

            MessageResponse r = authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "111111"));

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(r.message()).contains("phone verification");
            verify(userRepository).save(user);
        }

        @Test
        void verifyEmailReportsFullyVerifiedWhenPhoneAlreadyVerified() {
            user.setPhoneVerified(true);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(any(), any(), any())).thenReturn(true);

            assertThat(authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "1")).message())
                    .contains("fully verified");
        }

        @Test
        void verifyEmailUnknownUser() {
            when(userRepository.findByEmail("x@y.z")).thenReturn(Optional.empty());

            assertApiException(() -> authService.verifyEmail(new VerifyOtpRequest("x@y.z", "1")),
                    HttpStatus.NOT_FOUND, "No account");
        }

        @Test
        void verifyEmailBadOtp() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(any(), any(), any())).thenReturn(false);

            assertApiException(() -> authService.verifyEmail(new VerifyOtpRequest(user.getEmail(), "0")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired");

            assertThat(user.isEmailVerified()).isFalse();
            verify(userRepository, never()).save(any());
        }

        @Test
        void verifyPhoneMarksVerified() {
            when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));
            when(phoneOtpVerifier.verify(user.getPhoneNumber(), "222222")).thenReturn(true);

            MessageResponse r = authService.verifyPhone(new VerifyOtpRequest(user.getPhoneNumber(), "222222"));

            assertThat(user.isPhoneVerified()).isTrue();
            assertThat(r.message()).contains("email verification");
        }

        @Test
        void verifyPhoneFullyVerified() {
            user.setEmailVerified(true);
            when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));
            when(phoneOtpVerifier.verify(any(), any())).thenReturn(true);

            assertThat(authService.verifyPhone(new VerifyOtpRequest(user.getPhoneNumber(), "1")).message())
                    .contains("fully verified");
        }

        @Test
        void verifyPhoneUnknownAndInvalid() {
            when(userRepository.findByPhoneNumber("+91000")).thenReturn(Optional.empty());
            assertApiException(() -> authService.verifyPhone(new VerifyOtpRequest("+91000", "1")),
                    HttpStatus.NOT_FOUND, "phone");

            when(userRepository.findByPhoneNumber(user.getPhoneNumber())).thenReturn(Optional.of(user));
            when(phoneOtpVerifier.verify(any(), any())).thenReturn(false);
            assertApiException(() -> authService.verifyPhone(new VerifyOtpRequest(user.getPhoneNumber(), "1")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired");
        }
    }

    @Nested
    class Login {
        private final LoginRequest req = new LoginRequest("ash@pallet.town", "pw");

        @Test
        void sendsSecondFactorOtpOnValidCredentials() {
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.of(user));
            when(emailOtpService.generateAndStore(user.getEmail(), OtpPurpose.LOGIN_2FA)).thenReturn("654321");

            MessageResponse r = authService.login(req);

            verify(authenticationManager).authenticate(any());
            ArgumentCaptor<OtpNotificationMessage> cap = ArgumentCaptor.forClass(OtpNotificationMessage.class);
            verify(otpProducer).publish(cap.capture());
            assertThat(cap.getValue().purpose()).isEqualTo(OtpPurpose.LOGIN_2FA);
            assertThat(cap.getValue().otpCode()).isEqualTo("654321");
            assertThat(r.message()).contains("Password correct");
        }

        @Test
        void disabledAccountIsForbiddenAndAudited() {
            when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("nope"));

            assertApiException(() -> authService.login(req), HttpStatus.FORBIDDEN, "not fully verified");

            ArgumentCaptor<LoginFailedEvent> ev = ArgumentCaptor.forClass(LoginFailedEvent.class);
            verify(domainEventPublisher).publish(ev.capture());
            assertThat(ev.getValue().reason()).isEqualTo("ACCOUNT_NOT_VERIFIED");
            verifyNoInteractions(otpProducer);
        }

        @Test
        void badCredentialsAreUnauthorizedAndAudited() {
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

            assertApiException(() -> authService.login(req), HttpStatus.UNAUTHORIZED, "Invalid email or password");

            ArgumentCaptor<LoginFailedEvent> ev = ArgumentCaptor.forClass(LoginFailedEvent.class);
            verify(domainEventPublisher).publish(ev.capture());
            assertThat(ev.getValue().reason()).isEqualTo("BAD_CREDENTIALS");
        }

        @Test
        void userVanishingAfterAuthenticationIsUnauthorized() {
            when(userRepository.findByEmail(req.email())).thenReturn(Optional.empty());

            assertApiException(() -> authService.login(req), HttpStatus.UNAUTHORIZED, "Invalid");
        }
    }

    @Nested
    class VerifyLoginOtp {
        @Test
        void issuesHashedRefreshTokenAndBearerResponse() throws Exception {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(user.getEmail(), OtpPurpose.LOGIN_2FA, "123456")).thenReturn(true);
            stubTokenIssuance();

            AuthResponse r = authService.verifyLoginOtp(new VerifyOtpRequest(user.getEmail(), "123456"));

            assertThat(r.accessToken()).isEqualTo("access-jwt");
            assertThat(r.tokenType()).isEqualTo("Bearer");
            assertThat(r.expiresInSeconds()).isEqualTo(900L);
            assertThat(r.refreshToken()).isNotBlank();

            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());
            assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(r.refreshToken()))
                    .isNotEqualTo(r.refreshToken());
            assertThat(saved.getValue().getUser()).isSameAs(user);
            assertThat(saved.getValue().getExpiryDate()).isAfter(Instant.now());
            verify(domainEventPublisher).publish(any(LoginSuccessfulEvent.class));
        }

        @Test
        void passesNameClaimToJwt() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(any(), any(), any())).thenReturn(true);
            stubTokenIssuance();

            authService.verifyLoginOtp(new VerifyOtpRequest(user.getEmail(), "1"));

            verify(jwtService).generateAccessToken(userDetails, Map.of("name", "Ash"));
        }

        @Test
        void rejectsBadOtpWithoutIssuingTokens() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(any(), any(), any())).thenReturn(false);

            assertApiException(() -> authService.verifyLoginOtp(new VerifyOtpRequest(user.getEmail(), "0")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired");

            verifyNoInteractions(refreshTokenRepository, jwtService);
        }

        @Test
        void unknownUser() {
            when(userRepository.findByEmail("nobody@x.y")).thenReturn(Optional.empty());

            assertApiException(() -> authService.verifyLoginOtp(new VerifyOtpRequest("nobody@x.y", "1")),
                    HttpStatus.NOT_FOUND, "No account");
        }
    }

    @Nested
    class PasswordFlows {
        @Test
        void forgotPasswordSendsOtp() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.generateAndStore(user.getEmail(), OtpPurpose.FORGOT_PASSWORD)).thenReturn("999999");

            assertThat(authService.forgotPassword(new ForgotPasswordRequest(user.getEmail())).message())
                    .contains("reset code sent");
            verify(otpProducer).publish(any());
        }

        @Test
        void forgotPasswordUnknownEmail() {
            when(userRepository.findByEmail(any())).thenReturn(Optional.empty());

            assertApiException(() -> authService.forgotPassword(new ForgotPasswordRequest("x@y.z")),
                    HttpStatus.NOT_FOUND, "No account");
        }

        @Test
        void verifyForgotPasswordOtpReturnsResetToken() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(user.getEmail(), OtpPurpose.FORGOT_PASSWORD, "1")).thenReturn(true);
            when(passwordResetTokenService.generateToken(user.getEmail())).thenReturn("reset-123");

            ResetTokenResponse r = authService.verifyForgotPasswordOtp(new VerifyOtpRequest(user.getEmail(), "1"));

            assertThat(r.resetToken()).isEqualTo("reset-123");
        }

        @Test
        void verifyForgotPasswordOtpRejectsBadCode() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.verify(any(), any(), any())).thenReturn(false);

            assertApiException(() -> authService.verifyForgotPasswordOtp(new VerifyOtpRequest(user.getEmail(), "1")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired");
            verifyNoInteractions(passwordResetTokenService);
        }

        @Test
        void resetPasswordUpdatesHashRevokesSessionsAndPublishes() {
            when(passwordResetTokenService.consumeToken("tok")).thenReturn(Optional.of(user.getEmail()));
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.encode("newpassword")).thenReturn("new-hash");

            authService.resetPassword(new ResetPasswordRequest("tok", "newpassword"));

            assertThat(user.getPassword()).isEqualTo("new-hash");
            verify(userRepository).save(user);
            verify(refreshTokenRepository).deleteByUser(user);
            verify(domainEventPublisher).publish(any(PasswordChangedEvent.class));
        }

        @Test
        void resetPasswordInvalidToken() {
            when(passwordResetTokenService.consumeToken("bad")).thenReturn(Optional.empty());

            assertApiException(() -> authService.resetPassword(new ResetPasswordRequest("bad", "newpassword")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired reset token");
            verifyNoInteractions(userRepository);
        }

        @Test
        void resetPasswordAccountGone() {
            when(passwordResetTokenService.consumeToken("tok")).thenReturn(Optional.of("gone@x.y"));
            when(userRepository.findByEmail("gone@x.y")).thenReturn(Optional.empty());

            assertApiException(() -> authService.resetPassword(new ResetPasswordRequest("tok", "newpassword")),
                    HttpStatus.NOT_FOUND, "no longer exists");
        }

        @Test
        void changePasswordSuccess() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("old", "encoded")).thenReturn(true);
            when(passwordEncoder.encode("brandnew1")).thenReturn("h2");

            authService.changePassword(user.getEmail(), new ChangePasswordRequest("old", "brandnew1"));

            assertThat(user.getPassword()).isEqualTo("h2");
            verify(domainEventPublisher).publish(any(PasswordChangedEvent.class));
        }

        @Test
        void changePasswordWrongCurrent() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(any(), any())).thenReturn(false);

            assertApiException(() -> authService.changePassword(user.getEmail(),
                    new ChangePasswordRequest("wrong", "brandnew1")), HttpStatus.BAD_REQUEST, "incorrect");

            verify(userRepository, never()).save(any());
            verifyNoInteractions(domainEventPublisher);
        }
    }

    @Nested
    class ContactChange {
        @Test
        void requestEmailChangeStoresPendingAndSendsOtpToNewAddress() {
            when(userRepository.existsByEmail("new@x.y")).thenReturn(false);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(emailOtpService.generateAndStore("new@x.y", OtpPurpose.EMAIL_CHANGE)).thenReturn("424242");

            authService.requestEmailChange(user.getEmail(), new ChangeEmailRequest("new@x.y"));

            verify(pendingChangeService).storePendingEmail(user.getId(), "new@x.y");
            ArgumentCaptor<OtpNotificationMessage> cap = ArgumentCaptor.forClass(OtpNotificationMessage.class);
            verify(otpProducer).publish(cap.capture());
            assertThat(cap.getValue().recipient()).isEqualTo("new@x.y");
        }

        @Test
        void requestEmailChangeConflict() {
            when(userRepository.existsByEmail("taken@x.y")).thenReturn(true);

            assertApiException(() -> authService.requestEmailChange(user.getEmail(),
                    new ChangeEmailRequest("taken@x.y")), HttpStatus.CONFLICT, "already in use");
            verifyNoInteractions(pendingChangeService);
        }

        @Test
        void verifyEmailChangeUpdatesUserAndPublishes() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(pendingChangeService.consumePendingEmail(user.getId())).thenReturn(Optional.of("new@x.y"));
            when(emailOtpService.verify("new@x.y", OtpPurpose.EMAIL_CHANGE, "1")).thenReturn(true);
            String old = user.getEmail();

            authService.verifyEmailChange(old, new OtpOnlyRequest("1"));

            assertThat(user.getEmail()).isEqualTo("new@x.y");
            assertThat(user.isEmailVerified()).isTrue();
            ArgumentCaptor<EmailChangedEvent> ev = ArgumentCaptor.forClass(EmailChangedEvent.class);
            verify(domainEventPublisher).publish(ev.capture());
            assertThat(ev.getValue().oldEmail()).isEqualTo(old);
            assertThat(ev.getValue().newEmail()).isEqualTo("new@x.y");
        }

        @Test
        void verifyEmailChangeWithoutPending() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(pendingChangeService.consumePendingEmail(user.getId())).thenReturn(Optional.empty());

            assertApiException(() -> authService.verifyEmailChange(user.getEmail(), new OtpOnlyRequest("1")),
                    HttpStatus.BAD_REQUEST, "No pending email change");
        }

        @Test
        void verifyEmailChangeBadOtpLeavesEmailUntouched() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(pendingChangeService.consumePendingEmail(user.getId())).thenReturn(Optional.of("new@x.y"));
            when(emailOtpService.verify(any(), any(), any())).thenReturn(false);

            assertApiException(() -> authService.verifyEmailChange(user.getEmail(), new OtpOnlyRequest("0")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired");
            assertThat(user.getEmail()).isEqualTo("ash@pallet.town");
        }

        @Test
        void requestPhoneChangeSendsSms() {
            when(userRepository.existsByPhoneNumber("+911111111111")).thenReturn(false);
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

            authService.requestPhoneChange(user.getEmail(), new ChangePhoneRequest("+911111111111"));

            verify(pendingChangeService).storePendingPhone(user.getId(), "+911111111111");
            ArgumentCaptor<OtpNotificationMessage> cap = ArgumentCaptor.forClass(OtpNotificationMessage.class);
            verify(otpProducer).publish(cap.capture());
            assertThat(cap.getValue().channel()).isEqualTo(OtpChannel.SMS);
        }

        @Test
        void requestPhoneChangeConflict() {
            when(userRepository.existsByPhoneNumber(any())).thenReturn(true);

            assertApiException(() -> authService.requestPhoneChange(user.getEmail(),
                    new ChangePhoneRequest("+911111111111")), HttpStatus.CONFLICT, "already in use");
        }

        @Test
        void verifyPhoneChangeUpdatesUser() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(pendingChangeService.consumePendingPhone(user.getId())).thenReturn(Optional.of("+912222222222"));
            when(phoneOtpVerifier.verify("+912222222222", "7")).thenReturn(true);

            authService.verifyPhoneChange(user.getEmail(), new OtpOnlyRequest("7"));

            assertThat(user.getPhoneNumber()).isEqualTo("+912222222222");
            assertThat(user.isPhoneVerified()).isTrue();
            verify(domainEventPublisher).publish(any(PhoneChangedEvent.class));
        }

        @Test
        void verifyPhoneChangeNoPendingOrBadOtp() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(pendingChangeService.consumePendingPhone(user.getId()))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of("+912222222222"));
            assertApiException(() -> authService.verifyPhoneChange(user.getEmail(), new OtpOnlyRequest("1")),
                    HttpStatus.BAD_REQUEST, "No pending phone change");

            when(phoneOtpVerifier.verify(any(), any())).thenReturn(false);
            assertApiException(() -> authService.verifyPhoneChange(user.getEmail(), new OtpOnlyRequest("1")),
                    HttpStatus.BAD_REQUEST, "Invalid or expired");
            assertThat(user.getPhoneNumber()).isEqualTo("+919876543210");
        }
    }

    @Nested
    class OAuth2Exchange {
        @Test
        void exchangesValidCodeForTokens() {
            when(oAuth2ExchangeService.consumeCode("code")).thenReturn(Optional.of(user.getId()));
            when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
            stubTokenIssuance();

            AuthResponse r = authService.exchangeOAuth2Code("code");

            assertThat(r.accessToken()).isEqualTo("access-jwt");
            verify(domainEventPublisher).publish(any(LoginSuccessfulEvent.class));
        }

        @Test
        void invalidCode() {
            when(oAuth2ExchangeService.consumeCode("bad")).thenReturn(Optional.empty());

            assertApiException(() -> authService.exchangeOAuth2Code("bad"), HttpStatus.UNAUTHORIZED, "exchange code");
        }

        @Test
        void accountDeletedAfterCodeIssued() {
            UUID id = UUID.randomUUID();
            when(oAuth2ExchangeService.consumeCode("c")).thenReturn(Optional.of(id));
            when(userRepository.findById(id)).thenReturn(Optional.empty());

            assertApiException(() -> authService.exchangeOAuth2Code("c"), HttpStatus.NOT_FOUND, "no longer exists");
        }
    }

    @Nested
    class Refresh {
        @Test
        void rotatesRefreshToken() throws Exception {
            RefreshToken stored = RefreshToken.builder().user(user)
                    .tokenHash(sha256("raw")).expiryDate(Instant.now().plus(1, ChronoUnit.HOURS)).build();
            when(refreshTokenRepository.findByTokenHash(sha256("raw"))).thenReturn(Optional.of(stored));
            stubTokenIssuance();

            AuthResponse r = authService.refresh(new RefreshTokenRequest("raw"));

            verify(refreshTokenRepository).delete(stored);
            ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(saved.capture());
            assertThat(r.refreshToken()).isNotEqualTo("raw");
            assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(r.refreshToken()));
        }

        @Test
        void unknownTokenRejected() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            assertApiException(() -> authService.refresh(new RefreshTokenRequest("x")),
                    HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        @Test
        void expiredTokenIsDeletedAndRejected() {
            RefreshToken stored = RefreshToken.builder().user(user).tokenHash("h")
                    .expiryDate(Instant.now().minusSeconds(5)).build();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

            assertApiException(() -> authService.refresh(new RefreshTokenRequest("x")),
                    HttpStatus.UNAUTHORIZED, "expired");

            verify(refreshTokenRepository).delete(stored);
            verify(refreshTokenRepository, never()).save(any());
            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    class LogoutAndSessions {
        @Test
        void logoutDeletesTokenAndPublishes() {
            RefreshToken stored = RefreshToken.builder().user(user).tokenHash("h")
                    .expiryDate(Instant.now().plusSeconds(60)).build();
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));

            authService.logout(new RefreshTokenRequest("raw"));

            verify(refreshTokenRepository).delete(stored);
            verify(domainEventPublisher).publish(any(UserLoggedOutEvent.class));
        }

        @Test
        void logoutWithUnknownTokenIsNoOp() {
            when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

            authService.logout(new RefreshTokenRequest("raw"));

            verify(refreshTokenRepository, never()).delete(any());
            verifyNoInteractions(domainEventPublisher);
        }

        @Test
        void listSessionsClampsPagingAndMaps() {
            RefreshToken t = RefreshToken.builder().id(UUID.randomUUID()).user(user).tokenHash("h")
                    .expiryDate(Instant.now().plusSeconds(60)).createdAt(Instant.now()).build();
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(refreshTokenRepository.findByUserOrderByCreatedAtDesc(eq(user), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(t), PageRequest.of(0, 100), 1));

            SessionPageResponse r = authService.listMySessions(user.getEmail(), -5, 1000);

            ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
            verify(refreshTokenRepository).findByUserOrderByCreatedAtDesc(eq(user), pg.capture());
            assertThat(pg.getValue().getPageNumber()).isZero();
            assertThat(pg.getValue().getPageSize()).isEqualTo(100);
            assertThat(r.content()).hasSize(1);
            assertThat(r.content().get(0).id()).isEqualTo(t.getId());
            assertThat(r.totalElements()).isEqualTo(1);
        }

        @Test
        void listSessionsEnforcesMinimumSize() {
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(refreshTokenRepository.findByUserOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            authService.listMySessions(user.getEmail(), 2, 0);

            ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
            verify(refreshTokenRepository).findByUserOrderByCreatedAtDesc(any(), pg.capture());
            assertThat(pg.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pg.getValue().getPageSize()).isEqualTo(1);
        }

        @Test
        void revokeSessionDeletes() {
            UUID sid = UUID.randomUUID();
            RefreshToken session = RefreshToken.builder().id(sid).user(user).tokenHash("h")
                    .expiryDate(Instant.now().plusSeconds(60)).build();
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(refreshTokenRepository.findByIdAndUser(sid, user)).thenReturn(Optional.of(session));

            authService.revokeMySession(user.getEmail(), sid);

            verify(refreshTokenRepository).delete(session);
        }

        @Test
        void revokeSessionNotOwnedOrMissing() {
            UUID sid = UUID.randomUUID();
            when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
            when(refreshTokenRepository.findByIdAndUser(sid, user)).thenReturn(Optional.empty());

            assertApiException(() -> authService.revokeMySession(user.getEmail(), sid),
                    HttpStatus.NOT_FOUND, sid.toString());
            verify(refreshTokenRepository, never()).delete(any());
        }
    }
}
