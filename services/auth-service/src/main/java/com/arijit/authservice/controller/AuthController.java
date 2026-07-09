package com.arijit.authservice.controller;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.repository.UserRepository;
import com.arijit.authservice.service.AuthService;
import com.arijit.arceus.ratelimit.RateLimited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, verification, login, and account management")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @RateLimited(bucket = "register-ip", perIp = true, limit = 5, windowSeconds = 3600)
    @PostMapping("/register")
    @Operation(summary = "Register a new account - triggers email + SMS OTPs, no tokens yet")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @RateLimited(bucket = "verify-email", key = "#request.identifier()", limit = 8, windowSeconds = 600)
    @PostMapping("/verify-email")
    @Operation(summary = "Confirm the email OTP sent at registration")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyEmail(request));
    }

    @RateLimited(bucket = "verify-phone", key = "#request.identifier()", limit = 8, windowSeconds = 600)
    @PostMapping("/verify-phone")
    @Operation(summary = "Confirm the SMS OTP sent at registration")
    public ResponseEntity<MessageResponse> verifyPhone(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyPhone(request));
    }

    @RateLimited(bucket = "login-ip", perIp = true, limit = 20, windowSeconds = 900)
    @RateLimited(bucket = "login-user", key = "#request.email()", limit = 5, windowSeconds = 900)
    @PostMapping("/login")
    @Operation(summary = "Step 1 of login: checks password, triggers a login OTP email")
    public ResponseEntity<MessageResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @RateLimited(bucket = "login-otp-verify", key = "#request.identifier()", limit = 8, windowSeconds = 600)
    @PostMapping("/verify-login-otp")
    @Operation(summary = "Step 2 of login: confirms the OTP and returns actual tokens")
    public ResponseEntity<AuthResponse> verifyLoginOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyLoginOtp(request));
    }

    @RateLimited(bucket = "forgot-password", key = "#request.email()", limit = 3, windowSeconds = 3600)
    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset code via email")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @RateLimited(bucket = "forgot-password-verify", key = "#request.identifier()", limit = 8, windowSeconds = 600)
    @PostMapping("/verify-forgot-password-otp")
    @Operation(summary = "Confirm the reset code, get a one-time reset token back")
    public ResponseEntity<ResetTokenResponse> verifyForgotPasswordOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verifyForgotPasswordOtp(request));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Set a new password using the reset token")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }

    @RateLimited(bucket = "change-password", key = "#userDetails.username", limit = 5, windowSeconds = 900)
    @PostMapping("/change-password")
    @Operation(summary = "Change password while logged in")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        return ResponseEntity.ok(authService.changePassword(userDetails.getUsername(), request));
    }

    @RateLimited(bucket = "change-email-request", key = "#userDetails.username", limit = 3, windowSeconds = 3600)
    @PostMapping("/change-email/request")
    @Operation(summary = "Step 1: request a code sent to the new email")
    public ResponseEntity<MessageResponse> requestEmailChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangeEmailRequest request
    ) {
        return ResponseEntity.ok(authService.requestEmailChange(userDetails.getUsername(), request));
    }

    @RateLimited(bucket = "change-email-verify", key = "#userDetails.username", limit = 8, windowSeconds = 600)
    @PostMapping("/change-email/verify")
    @Operation(summary = "Step 2: confirm the code, email is updated")
    public ResponseEntity<MessageResponse> verifyEmailChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OtpOnlyRequest request
    ) {
        return ResponseEntity.ok(authService.verifyEmailChange(userDetails.getUsername(), request));
    }

    @RateLimited(bucket = "change-phone-request", key = "#userDetails.username", limit = 3, windowSeconds = 3600)
    @PostMapping("/change-phone/request")
    @Operation(summary = "Step 1: request a code sent to the new phone number")
    public ResponseEntity<MessageResponse> requestPhoneChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePhoneRequest request
    ) {
        return ResponseEntity.ok(authService.requestPhoneChange(userDetails.getUsername(), request));
    }

    @RateLimited(bucket = "change-phone-verify", key = "#userDetails.username", limit = 8, windowSeconds = 600)
    @PostMapping("/change-phone/verify")
    @Operation(summary = "Step 2: confirm the code, phone number is updated")
    public ResponseEntity<MessageResponse> verifyPhoneChange(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody OtpOnlyRequest request
    ) {
        return ResponseEntity.ok(authService.verifyPhoneChange(userDetails.getUsername(), request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @RateLimited(bucket = "oauth2-exchange", key = "#request.code()", limit = 5, windowSeconds = 60)
    @PostMapping("/oauth2/exchange")
    @Operation(summary = "Exchange the single-use code from the OAuth2 redirect for real tokens")
    public ResponseEntity<AuthResponse> exchangeOAuth2Code(@Valid @RequestBody OAuth2ExchangeRequest request) {
        return ResponseEntity.ok(authService.exchangeOAuth2Code(request.code()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate a refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();

        return ResponseEntity.ok(new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(),
                user.isEmailVerified(), user.isPhoneVerified(), user.getProvider(), user.getRoles()
        ));
    }
}
