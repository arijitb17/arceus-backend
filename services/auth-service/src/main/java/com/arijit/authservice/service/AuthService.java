package com.arijit.authservice.service;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.messaging.OtpProducer;
import com.arijit.authservice.otp.EmailOtpService;
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

@Service
@RequiredArgsConstructor
public class AuthService {

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

    // ---------- Registration & core verification ----------

    @Transactional
    public MessageResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException("An account with this email already exists", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByPhoneNumber(request.phoneNumber())) {
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
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        return new MessageResponse(user.isFullyVerified()
                ? "Email verified. Your account is fully verified - you can log in now."
                : "Email verified. Still waiting on phone verification.");
    }

    @Transactional
    public MessageResponse verifyPhone(VerifyOtpRequest request) {
        User user = userRepository.findByPhoneNumber(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this phone number", HttpStatus.NOT_FOUND));

        if (!phoneOtpVerifier.verify(request.identifier(), request.otp())) {
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        user.setPhoneVerified(true);
        userRepository.save(user);

        return new MessageResponse(user.isFullyVerified()
                ? "Phone verified. Your account is fully verified - you can log in now."
                : "Phone verified. Still waiting on email verification.");
    }

    // ---------- Login (2 steps) ----------

    public MessageResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (DisabledException e) {
            domainEventPublisher.publish(LoginFailedEvent.now(request.email(), "ACCOUNT_NOT_VERIFIED"));
            throw new ApiException("Account not fully verified yet", HttpStatus.FORBIDDEN);
        } catch (AuthenticationException e) {
            domainEventPublisher.publish(LoginFailedEvent.now(request.email(), "BAD_CREDENTIALS"));
            throw new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("Invalid email or password", HttpStatus.UNAUTHORIZED));

        sendEmailOtp(user.getEmail(), user.getName(), OtpPurpose.LOGIN_2FA);

        return new MessageResponse("Password correct. Enter the code sent to your email to finish logging in.");
    }

    @Transactional
    public AuthResponse verifyLoginOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        if (!emailOtpService.verify(request.identifier(), OtpPurpose.LOGIN_2FA, request.otp())) {
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        domainEventPublisher.publish(LoginSuccessfulEvent.now(user.getId(), user.getEmail()));

        return issueTokens(user);
    }

    // ---------- Forgot password ----------

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        sendEmailOtp(user.getEmail(), user.getName(), OtpPurpose.FORGOT_PASSWORD);
        return new MessageResponse("Password reset code sent to your email.");
    }

    @Transactional
    public ResetTokenResponse verifyForgotPasswordOtp(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.identifier())
                .orElseThrow(() -> new ApiException("No account with this email", HttpStatus.NOT_FOUND));

        if (!emailOtpService.verify(request.identifier(), OtpPurpose.FORGOT_PASSWORD, request.otp())) {
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        String resetToken = passwordResetTokenService.generateToken(user.getEmail());
        return new ResetTokenResponse(resetToken, "Code verified. Use this token to set a new password within 10 minutes.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String email = passwordResetTokenService.consumeToken(request.resetToken())
                .orElseThrow(() -> new ApiException("Invalid or expired reset token", HttpStatus.BAD_REQUEST));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("Account no longer exists", HttpStatus.NOT_FOUND));

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Reset means "assume the old session may be compromised" - kill every
        // existing refresh token so all devices are forced to log in again.
        refreshTokenRepository.deleteByUser(user);

        domainEventPublisher.publish(PasswordChangedEvent.now(user.getId(), user.getEmail()));

        return new MessageResponse("Password reset. Please log in with your new password.");
    }

    // ---------- Change password (authenticated) ----------

    @Transactional
    public MessageResponse changePassword(String currentUserEmail, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ApiException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        domainEventPublisher.publish(PasswordChangedEvent.now(user.getId(), user.getEmail()));
        return new MessageResponse("Password changed.");
    }

    // ---------- Change email (authenticated, two steps) ----------

    @Transactional
    public MessageResponse requestEmailChange(String currentUserEmail, ChangeEmailRequest request) {
        if (userRepository.existsByEmail(request.newEmail())) {
            throw new ApiException("That email is already in use", HttpStatus.CONFLICT);
        }

        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        pendingChangeService.storePendingEmail(user.getId(), request.newEmail());
        sendEmailOtp(request.newEmail(), user.getName(), OtpPurpose.EMAIL_CHANGE);

        return new MessageResponse("Verification code sent to the new email address.");
    }

    @Transactional
    public MessageResponse verifyEmailChange(String currentUserEmail, OtpOnlyRequest request) {
        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        String newEmail = pendingChangeService.consumePendingEmail(user.getId())
                .orElseThrow(() -> new ApiException("No pending email change - request one first", HttpStatus.BAD_REQUEST));

        if (!emailOtpService.verify(newEmail, OtpPurpose.EMAIL_CHANGE, request.otp())) {
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        user.setEmailVerified(true);
        userRepository.save(user);
        domainEventPublisher.publish(EmailChangedEvent.now(user.getId(), oldEmail, newEmail));
        return new MessageResponse("Email updated.");
    }

    // ---------- Change phone (authenticated, two steps) ----------

    @Transactional
    public MessageResponse requestPhoneChange(String currentUserEmail, ChangePhoneRequest request) {
        if (userRepository.existsByPhoneNumber(request.newPhoneNumber())) {
            throw new ApiException("That phone number is already in use", HttpStatus.CONFLICT);
        }

        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        pendingChangeService.storePendingPhone(user.getId(), request.newPhoneNumber());
        sendPhoneOtp(request.newPhoneNumber());

        return new MessageResponse("Verification code sent to the new phone number.");
    }

    @Transactional
    public MessageResponse verifyPhoneChange(String currentUserEmail, OtpOnlyRequest request) {
        User user = userRepository.findByEmail(currentUserEmail).orElseThrow();
        String newPhone = pendingChangeService.consumePendingPhone(user.getId())
                .orElseThrow(() -> new ApiException("No pending phone change - request one first", HttpStatus.BAD_REQUEST));

        if (!phoneOtpVerifier.verify(newPhone, request.otp())) {
            throw new ApiException("Invalid or expired code", HttpStatus.BAD_REQUEST);
        }

        String oldPhone = user.getPhoneNumber();
        user.setPhoneNumber(newPhone);
        user.setPhoneVerified(true);
        userRepository.save(user);
        domainEventPublisher.publish(PhoneChangedEvent.now(user.getId(), oldPhone, newPhone));
        return new MessageResponse("Phone number updated.");
    }

    // ---------- Refresh / logout ----------

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new ApiException("Refresh token expired, please log in again", HttpStatus.UNAUTHORIZED);
        }

        User user = stored.getUser();
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String newAccessToken = jwtService.generateAccessToken(userDetails, Map.of("name", user.getName()));

        refreshTokenRepository.delete(stored);
        String newRefreshToken = jwtService.generateRefreshToken(userDetails);
        saveRefreshToken(user, newRefreshToken);

        return AuthResponse.of(newAccessToken, newRefreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenRepository.findByToken(request.refreshToken())
                .ifPresent(stored -> {
                    User user = stored.getUser();
                    refreshTokenRepository.delete(stored);
                    domainEventPublisher.publish(UserLoggedOutEvent.now(user.getId(), user.getEmail()));
                });
    }

    // ---------- Shared helpers ----------

    private void sendEmailOtp(String email, String name, OtpPurpose purpose) {
        String otp = emailOtpService.generateAndStore(email, purpose);
        otpProducer.publish(new OtpNotificationMessage(OtpChannel.EMAIL, email, otp, purpose, name));
    }

    private void sendPhoneOtp(String phoneNumber) {
        otpProducer.publish(new OtpNotificationMessage(OtpChannel.SMS, phoneNumber, null, com.arijit.arceus.events.otp.OtpPurpose.PHONE_VERIFICATION, null));
    }

    private AuthResponse issueTokens(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, Map.of("name", user.getName()));
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        saveRefreshToken(user, refreshToken);
        return AuthResponse.of(accessToken, refreshToken, jwtService.getAccessTokenExpirationSeconds());
    }

    private void saveRefreshToken(User user, String token) {
        refreshTokenRepository.save(RefreshToken.builder()
                .token(token)
                .user(user)
                .expiryDate(Instant.now().plusMillis(jwtService.getRefreshTokenExpirationMs()))
                .build());
    }
}
