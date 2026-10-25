package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(
        @NotBlank String identifier,
        @NotBlank String otp
) {}
