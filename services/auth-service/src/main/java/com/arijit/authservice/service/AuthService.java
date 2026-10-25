package com.arijit.authservice.service;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.messaging.OtpProducer;
import com.arijit.authservice.otp.EmailOtpService;
import com.arijit.authservice.otp.OAuth2ExchangeService;
import com.arijit.authservice.otp.PasswordResetTokenService;
import com.arijit.authservice.otp.PendingChangeService;
import com.arijit.authservice.otp.PhoneOtpVerifier;
import com.arijit.authservice.repository.RefreshTokenRepository;
import com.arijit.authservice.repository.UserRepository;
import com.arijit.authservice.security.JwtService;
import com.arijit.arceus.events.domain.EmailChangedEvent;
import com.arijit.arceus.events.domain.LoginFailedEvent;
import com.arijit.arceus.events.domain.LoginSuccessfulEvent;
import com.arijit.arceus.events.domain.PasswordChangedEvent;
import com.arijit.arceus.events.domain.PhoneChangedEvent;
import com.arijit.arceus.events.domain.UserLoggedOutEvent;
import com.arijit.arceus.events.domain.UserRegisteredEvent;
import com.arijit.arceus.events.otp.OtpChannel;
import com.arijit.arceus.events.otp.OtpNotificationMessage;
import com.arijit.arceus.events.otp.OtpPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.arijit.arceus.events.logging.LogMasker;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    private final EmailOtpService emailOtpService;
    private final PhoneOtpVerifier phoneOtpVerifier;
    private final OtpProducer otpProducer;
    private final PasswordResetTokenService passwordResetTokenService;
    private final PendingChangeService pendingChangeService;
    private final DomainEventPublisher domainEventPublisher;
    private final OAuth2ExchangeService oAuth2ExchangeService;
    private final com.arijit.authservice.featureflag.FeatureFlagGateService featureFlagGateService;

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        featureFlagGateService.requireEnabled("auth.registration.enabled", "Registration");

        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration rejected: email {} already in use", LogMasker.email(request.email()));
            throw new ApiException("An account with this email already exists", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
            log.warn("Registration rejected: phone {} already in use", LogMasker.phone(request.phoneNumber()));
            throw new ApiException("An account with this phone number already exists", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .phoneNumber(request.phoneNumber())
                .password(passwordEncoder.encode(request.password()))
                .provider(AuthProvider.LOCAL)
                .build();

        userRepository.save(user);
        log.info("User registered: userId={} email={}", user.getId(), LogMasker.email(user.getEmail()));

        domainEventPublisher.publish(UserRegisteredEvent.now(user.getId(), user.getEmail(), user.getName()));

        sendEmailOtp(user.getEmail(), user.getName(), OtpPurpose.EMAIL_VERIFICATION);
        sendPhoneOtp(user.getPhoneNumber());

        return new MessageResponse(
                "Account created. Check your email and phone for verification codes before logging in."
        );
    }

    @Transactional
    public MessageResponse verifyEmail(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        if (!emailOtpService.verify(request.identifier(), OtpPurpose.EMAIL_VERIFICATION, request.otp())) {
            log.warn("Email verification failed: invalid or expired code for userId={}", user.getId());
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email verified: userId={} fullyVerified={}", user.getId(), user.isFullyVerified());

        return new MessageResponse(user.isFullyVerified()
                ? "Email verified. Your account is fully verified - you can log in now."
                : "Email verified. Still waiting on phone verification.");
    }

    @Transactional
    public MessageResponse verifyPhone(VerifyOtpRequest request) {
        User user = userRepository.findByPhoneNumber(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this phone number", HttpStatus.NOT_FOUND));

        if (!phoneOtpVerifier.verify(request.identifier(), request.otp())) {
            log.warn("Phone verification failed: invalid or expired code for userId={}", user.getId());
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        user.setPhoneVerified(true);
        userRepository.save(user);
        log.info("Phone verified: userId={} fullyVerified={}", user.getId(), user.isFullyVerified());

        return new MessageResponse(user.isFullyVerified()
                ? "Phone verified. Your account is fully verified - you can log in now."
                : "Phone verified. Still waiting on email verification.");
    }

    public MessageResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException e) {
            log.warn("Login rejected: account not fully verified, email={}", LogMasker.email(request.email()));
            domainEventPublisher.publish(LoginFailedEvent.now(request.email(), "ACCOUNT_NOT_VERIFIED"));
            throw new ApiException("Account not fully verified yet", HttpStatus.FORBIDDEN);
        } catch (AuthenticationException e) {
            log.warn("Login failed: bad credentials, email={}", LogMasker.email(request.email()));
            domainEventPublisher.publish(LoginFailedEvent.now(request.email(), "BAD_CREDENTIALS"));
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        sendEmailOtp(user.getEmail(), user.getName(), OtpPurpose.LOGIN_2FA);
        log.debug("Login password accepted, 2FA code dispatched: userId={}", user.getId());

        return new MessageResponse("Password correct. Enter the code sent to your email to finish logging in.");
    }

    @Transactional
    public AuthResponse verifyLoginOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        if (!emailOtpService.verify(request.identifier(), OtpPurpose.LOGIN_2FA, request.otp())) {
            log.warn("Login 2FA failed: invalid or expired code for userId={}", user.getId());
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        log.info("Login succeeded: userId={}", user.getId());
        domainEventPublisher.publish(LoginSuccessfulEvent.now(user.getId(), user.getEmail()));

        return issueTokens(user);
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        sendEmailOtp(user.getEmail(), user.getName(), OtpPurpose.FORGOT_PASSWORD);
        log.info("Password reset requested: userId={}", user.getId());
        return new MessageResponse("Password reset code sent to your email.");
    }

    @Transactional
    public ResetTokenResponse verifyForgotPasswordOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        if (!emailOtpService.verify(request.identifier(), OtpPurpose.FORGOT_PASSWORD, request.otp())) {
            log.warn("Password reset code rejected: invalid or expired code for userId={}", user.getId());
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        String resetToken = passwordResetTokenService.generateToken(user.getEmail());
        return new ResetTokenResponse(resetToken, "Code verified. Use this token to set a new password within 10 minutes.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = passwordResetTokenService.consumeToken(request.resetToken())
                .orElseThrow(() -> {
                    log.warn("Password reset rejected: invalid or expired reset token");
                    return new ApiException("Invalid or expired reset token", HttpStatus.BAD_REQUEST);
                });

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.warn("Password reset rejected: account no longer exists for email={}", LogMasker.email(email));
                    return new ApiException("Account no longer exists", HttpStatus.NOT_FOUND);
                });

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        refreshTokenRepository.deleteByUser(user);
        log.info("Password reset completed, all sessions revoked: userId={}", user.getId());

        domainEventPublisher.publish(PasswordChangedEvent.now(user.getId(), user.getEmail()));

        return new MessageResponse("Password reset. Please log in with your new password.");
    }

    @Transactional
    public MessageResponse changePassword(String currentUserEmail, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            log.warn("Password change rejected: current password incorrect for userId={}", user.getId());
            throw new ApiException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Password changed: userId={}", user.getId());
        domainEventPublisher.publish(PasswordChangedEvent.now(user.getId(), user.getEmail()));
        return new MessageResponse("Password changed.");
    }

    @Transactional
    public MessageResponse requestEmailChange(String currentUserEmail, ChangeEmailRequest request) {
        if (userRepository.existsByEmail(request.newEmail())) {
            log.warn("Email change rejected: new email {} already in use", LogMasker.email(request.newEmail()));
            throw new ApiException("That email is already in use", HttpStatus.CONFLICT);
        }

        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        pendingChangeService.storePendingEmail(user.getId(), request.newEmail());
        sendEmailOtp(request.newEmail(), user.getName(), OtpPurpose.EMAIL_CHANGE);
        log.info("Email change requested: userId={}", user.getId());

        return new MessageResponse("Verification code sent to the new email address.");
    }

    @Transactional
    public MessageResponse verifyEmailChange(String currentUserEmail, OtpOnlyRequest request) {
        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        String newEmail = pendingChangeService.consumePendingEmail(user.getId())
                .orElseThrow(() -> new ApiException("No pending email change - request one first", HttpStatus.BAD_REQUEST));

        if (!emailOtpService.verify(newEmail, OtpPurpose.EMAIL_CHANGE, request.otp())) {
            log.warn("Email change verification failed: invalid or expired code for userId={}", user.getId());
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.setEmailVerified(true);
        userRepository.save(user);
        log.info("Email changed: userId={}", user.getId());
        domainEventPublisher.publish(EmailChangedEvent.now(user.getId(), oldEmail, newEmail));
        return new MessageResponse("Email updated.");
    }

    @Transactional
    public MessageResponse requestPhoneChange(String currentUserEmail, ChangePhoneRequest request) {
        if (userRepository.existsByPhoneNumber(request.newPhoneNumber())) {
            log.warn("Phone change rejected: new phone {} already in use", LogMasker.phone(request.newPhoneNumber()));
            throw new ApiException("That phone number is already in use", HttpStatus.CONFLICT);
        }

        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        pendingChangeService.storePendingPhone(user.getId(), request.newPhoneNumber());
        sendPhoneOtp(request.newPhoneNumber());
        log.info("Phone change requested: userId={}", user.getId());

        return new MessageResponse("Verification code sent to the new phone number.");
    }

    @Transactional
    public MessageResponse verifyPhoneChange(String currentUserEmail, OtpOnlyRequest request) {
        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        String newPhone = pendingChangeService.consumePendingPhone(user.getId())
                .orElseThrow(() -> new ApiException("No pending phone change - request one first", HttpStatus.BAD_REQUEST));

        if (!phoneOtpVerifier.verify(newPhone, request.otp())) {
            log.warn("Phone change verification failed: invalid or expired code for userId={}", user.getId());
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        String oldPhone = user.getPhoneNumber();
        user.setPhoneNumber(newPhone);
        user.setPhoneVerified(true);
        userRepository.save(user);
        log.info("Phone changed: userId={}", user.getId());
        domainEventPublisher.publish(PhoneChangedEvent.now(user.getId(), oldPhone, newPhone));
        return new MessageResponse("Phone number updated.");
    }

    @Transactional
    public AuthResponse exchangeOAuth2Code(String code) {
        var userId = oAuth2ExchangeService.consumeCode(code)
                .orElseThrow(() -> {
                    log.warn("OAuth2 exchange rejected: invalid or expired exchange code");
                    return new ApiException("Invalid or expired exchange code", HttpStatus.UNAUTHORIZED);
                });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("OAuth2 exchange rejected: account no longer exists, userId={}", userId);
                    return new ApiException("Account no longer exists", HttpStatus.NOT_FOUND);
                });

        log.info("Login succeeded via OAuth2: userId={}", user.getId());
        domainEventPublisher.publish(LoginSuccessfulEvent.now(user.getId(), user.getEmail()));
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .orElseThrow(() -> {
                    log.warn("Token refresh rejected: unknown refresh token");
                    return new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
                });

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            log.info("Token refresh rejected: refresh token expired for userId={}", stored.getUser().getId());
            throw new ApiException("Refresh token expired, please log in again", HttpStatus.UNAUTHORIZED);
        }

        User user = stored.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateAccessToken(userDetails, Map.of("name", user.getName()));

        refreshTokenRepository.delete(stored);
        String newRefreshToken = generateOpaqueToken();
        saveRefreshToken(user, newRefreshToken);
        log.debug("Refresh token rotated: userId={}", user.getId());

        return AuthResponse.of(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .ifPresent(stored -> {
                    User user = stored.getUser();
                    refreshTokenRepository.delete(stored);
                    log.info("User logged out: userId={}", user.getId());
                    domainEventPublisher.publish(UserLoggedOutEvent.now(user.getId(), user.getEmail()));
                });
    }

    public SessionPageResponse listMySessions(String email, int page, int size) {
        User user = userRepository.findByEmail(email).orElseThrow();
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return SessionPageResponse.from(
                refreshTokenRepository.findByUserOrderByCreatedAtDesc(user, pageable).map(SessionResponse::from));
    }

    @Transactional
    public void revokeMySession(String email, java.util.UUID sessionId) {
        User user = userRepository.findByEmail(email).orElseThrow();
        RefreshToken session = refreshTokenRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> {
                    log.warn("Session revoke rejected: session {} not found for userId={}", sessionId, user.getId());
                    return new ApiException("No session " + sessionId, HttpStatus.NOT_FOUND);
                });
        refreshTokenRepository.delete(session);
        log.info("Session revoked: userId={} sessionId={}", user.getId(), sessionId);
    }

    private void sendEmailOtp(String email, String name, OtpPurpose purpose) {
        String otp = emailOtpService.generateAndStore(email, purpose);
        otpProducer.publish(OtpNotificationMessage.of(OtpChannel.EMAIL, email, otp, purpose, name));
    }

    private void sendPhoneOtp(String phoneNumber) {
        otpProducer.publish(OtpNotificationMessage.of(OtpChannel.SMS, phoneNumber, null, com.arijit.arceus.events.otp.OtpPurpose.PHONE_VERIFICATION, null));
    }

    private AuthResponse issueTokens(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, Map.of("name", user.getName()));
        String refreshToken = generateOpaqueToken();
        saveRefreshToken(user, refreshToken);
        return AuthResponse.of(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    private void saveRefreshToken(User user, String rawToken) {
        refreshTokenRepository.save(RefreshToken.builder()
                .tokenHash(hashToken(rawToken))
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build());
    }

    private static String generateOpaqueToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String hashToken(String rawToken) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
