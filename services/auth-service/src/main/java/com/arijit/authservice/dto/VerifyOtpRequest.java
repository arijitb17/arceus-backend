package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;

// Reused for all three OTP checks (email verification, phone verification,
// login 2FA) - "identifier" is an email for the first/third, a phone number
// for the second. Keeping one shape avoids three near-identical DTOs.
public record VerifyOtpRequest(
        @NotBlank String identifier,
        @NotBlank String otp
) {}
