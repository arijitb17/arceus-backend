package com.arijit.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuth2ExchangeRequest(
        @NotBlank String code
) {}
