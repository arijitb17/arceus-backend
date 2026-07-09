package com.arijit.authservice.controller;

import com.arijit.arceus.ratelimit.RateLimited;
import com.arijit.authservice.dto.ApiKeyCreatedResponse;
import com.arijit.authservice.dto.ApiKeyResponse;
import com.arijit.authservice.dto.CreateApiKeyRequest;
import com.arijit.authservice.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Self-service API key management - every endpoint here operates only on
 * the calling user's own keys (never another user's), so there's no
 * separate admin/ownership check needed beyond "you must be logged in".
 */
@RestController
@RequestMapping("/api/auth/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "Long-lived credentials for programmatic access, as an alternative to the JWT flow")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @RateLimited(bucket = "api-key-create", key = "#userDetails.username", limit = 10, windowSeconds = 3600)
    @PostMapping
    @Operation(summary = "Create a new API key - the raw key is returned exactly once, in this response only")
    public ResponseEntity<ApiKeyCreatedResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        String rawKey = apiKeyService.create(userDetails.getUsername(), request.name());
        // We don't have the generated id/name handy without a second lookup
        // (create() intentionally returns just the raw key, so nothing
        // that touches logs/traces ever holds the raw key alongside the
        // key's metadata) - listForOwner() is one cheap query and keeps
        // that boundary clean.
        var justCreated = apiKeyService.listForOwner(userDetails.getUsername()).get(0);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiKeyCreatedResponse(justCreated.getId(), justCreated.getName(), rawKey));
    }

    @GetMapping
    @Operation(summary = "List your own API keys - metadata only, never the raw key")
    public ResponseEntity<List<ApiKeyResponse>> list(@AuthenticationPrincipal UserDetails userDetails) {
        var keys = apiKeyService.listForOwner(userDetails.getUsername()).stream()
                .map(ApiKeyResponse::from)
                .toList();
        return ResponseEntity.ok(keys);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke one of your own API keys immediately")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal UserDetails userDetails, @PathVariable UUID id) {
        apiKeyService.revoke(userDetails.getUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
