package com.arijit.authservice.dto;

/** Response body for GET /api-keys/{id}/reveal - see ApiKeyCryptoService's javadoc for what this trades away. */
public record RevealedApiKeyResponse(String key) {
}
