package com.arijit.organizationservice.controller;

import com.arijit.organizationservice.dto.CreateSecretRequest;
import com.arijit.organizationservice.dto.SecretResponse;
import com.arijit.organizationservice.entity.EnvironmentType;
import com.arijit.organizationservice.service.SecretService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** See Secret's javadoc - no endpoint here ever returns a decrypted value. */
@RestController
@RequestMapping("/organizations/{organizationId}/projects/{projectId}/environments/{environment}/secrets")
@RequiredArgsConstructor
public class SecretController {

    private final SecretService secretService;

    @GetMapping
    public List<SecretResponse> list(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                       @PathVariable EnvironmentType environment, Principal principal) {
        return secretService.list(organizationId, projectId, environment, principal.getName()).stream()
                .map(SecretResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<SecretResponse> create(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                                   @PathVariable EnvironmentType environment,
                                                   @Valid @RequestBody CreateSecretRequest request,
                                                   Principal principal) {
        var secret = secretService.create(organizationId, projectId, environment, request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(SecretResponse.from(secret));
    }

    @DeleteMapping("/{secretKey}")
    public ResponseEntity<Void> revoke(@PathVariable UUID organizationId, @PathVariable UUID projectId,
                                        @PathVariable EnvironmentType environment, @PathVariable String secretKey,
                                        Principal principal) {
        secretService.revoke(organizationId, projectId, environment, secretKey, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
