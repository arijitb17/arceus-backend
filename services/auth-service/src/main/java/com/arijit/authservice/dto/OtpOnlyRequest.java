package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpOnlyRequest(
        @NotBlank String otp
) {}
