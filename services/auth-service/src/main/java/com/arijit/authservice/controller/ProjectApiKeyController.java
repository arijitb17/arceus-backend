package com.arijit.authservice.controller;

import com.arijit.arceus.ratelimit.RateLimited;
import com.arijit.authservice.dto.CreateProjectApiKeyRequest;
import com.arijit.authservice.dto.ProjectApiKeyCreatedResponse;
import com.arijit.authservice.dto.ProjectApiKeyResponse;
import com.arijit.authservice.dto.RevealedApiKeyResponse;
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
 * The Developer Portal's key-issuance surface (architecture design §7) -
 * distinct from ApiKeyController's pre-existing self-service platform-key
 * endpoints. Every method here authorizes against organization-service's
 * own OrgRole (OWNER/ADMIN required) via ApiKeyService, not against
 * "is this the key's owner" the way ApiKeyController's endpoints do,
 * since a project's keys are a project resource any org manager can
 * administer - not personal to whichever developer happened to click
 * "generate".
 * <p>
 * Mounted under {@code /api/auth/...} (not {@code /api/organizations/...})
 * because API keys are and remain auth-service's own resource - see the
 * architecture design's §7: "auth-service already has an ApiKey entity -
 * extended, not replaced".
 */
@RestController
@RequestMapping("/api/auth/organizations/{organizationId}/projects/{projectId}/api-keys")
@RequiredArgsConstructor
@Tag(name = "Project API Keys", description = "Project-scoped credentials generated SDKs authenticate with (architecture design §7)")
public class ProjectApiKeyController {

    private final ApiKeyService apiKeyService;

    @RateLimited(bucket = "project-api-key-create", key = "#userDetails.username", limit = 30, windowSeconds = 3600)
    @PostMapping
    @Operation(summary = "Generate a project-scoped API key")
    public ResponseEntity<ProjectApiKeyCreatedResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectApiKeyRequest request
    ) {
        String rawKey = apiKeyService.createProjectKey(userDetails.getUsername(), organizationId, projectId,
                request.environment(), request.keyType(), request.name(), request.scopes());

        // Same reasoning as ApiKeyController#create for the extra lookup -
        // create() intentionally returns only the raw key.
        var justCreated = apiKeyService.listForProject(userDetails.getUsername(), organizationId, projectId, request.environment())
                .get(0);

        return ResponseEntity.status(HttpStatus.CREATED).body(new ProjectApiKeyCreatedResponse(
                justCreated.getId(), justCreated.getName(), justCreated.getKeyType().name(),
                justCreated.getEnvironment(), rawKey));
    }

    @GetMapping
    @Operation(summary = "List a project's API keys for one environment - metadata and prefix only, never the raw key")
    public ResponseEntity<List<ProjectApiKeyResponse>> list(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @RequestParam String environment
    ) {
        var keys = apiKeyService.listForProject(userDetails.getUsername(), organizationId, projectId, environment).stream()
                .map(ProjectApiKeyResponse::from)
                .toList();
        return ResponseEntity.ok(keys);
    }

    @RateLimited(bucket = "project-api-key-reveal", key = "#userDetails.username", limit = 60, windowSeconds = 3600)
    @GetMapping("/{id}/reveal")
    @Operation(summary = "Decrypt and return the raw key again - see ApiKey's javadoc for the trade-off this represents")
    public ResponseEntity<RevealedApiKeyResponse> reveal(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID id
    ) {
        String rawKey = apiKeyService.revealProjectKey(userDetails.getUsername(), organizationId, projectId, id);
        return ResponseEntity.ok(new RevealedApiKeyResponse(rawKey));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke a project API key immediately")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID id
    ) {
        apiKeyService.revokeProjectKey(userDetails.getUsername(), organizationId, projectId, id);
        return ResponseEntity.noContent().build();
    }
}
