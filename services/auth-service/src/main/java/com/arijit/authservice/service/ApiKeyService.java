package com.arijit.authservice.service;

import com.arijit.arceus.events.domain.ApiKeyCreatedEvent;
import com.arijit.arceus.events.domain.ApiKeyRevokedEvent;
import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.messaging.DomainEventPublisher;
import com.arijit.authservice.repository.ApiKeyRepository;
import com.arijit.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final String KEY_PREFIX_LITERAL = "ak_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final DomainEventPublisher domainEventPublisher;

    /**
     * @return the raw key - this is the ONLY time it's ever available.
     *         Only its hash is persisted; there is no "reveal key" endpoint,
     *         the same way there's no "reveal password" endpoint.
     */
    @Transactional
    public String create(String ownerEmail, String name) {
        User owner = requireUser(ownerEmail);
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);
        String rawKey = KEY_PREFIX_LITERAL + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        ApiKey apiKey = apiKeyRepository.save(ApiKey.builder()
                .user(owner)
                .name(name)
                .keyHash(sha256Hex(rawKey))
                .keyPrefix(rawKey.substring(0, 8))
                .build());

        domainEventPublisher.publish(ApiKeyCreatedEvent.now(owner.getId(), owner.getEmail(), apiKey.getId(), apiKey.getKeyPrefix(), name));
        return rawKey;
    }

    public List<ApiKey> listForOwner(String ownerEmail) {
        return apiKeyRepository.findByUserOrderByCreatedAtDesc(requireUser(ownerEmail));
    }

    @Transactional
    public void revoke(String ownerEmail, UUID apiKeyId) {
        User owner = requireUser(ownerEmail);
        ApiKey apiKey = apiKeyRepository.findByIdAndUser(apiKeyId, owner)
                .orElseThrow(() -> new ApiException("No API key with id " + apiKeyId, HttpStatus.NOT_FOUND));
        apiKey.setRevoked(true);
        apiKeyRepository.save(apiKey);
        domainEventPublisher.publish(ApiKeyRevokedEvent.now(owner.getId(), owner.getEmail(), apiKeyId));
    }

    /**
     * Validates a raw key presented in an X-API-Key header and, if valid,
     * updates its lastUsedAt timestamp. Returns empty for anything wrong -
     * unknown hash, revoked - so ApiKeyAuthFilter can treat every failure
     * mode the same way (fall through unauthenticated).
     */
    @Transactional
    public Optional<User> validateAndTouch(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX_LITERAL)) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(sha256Hex(rawKey))
                .filter(k -> !k.isRevoked())
                .map(k -> {
                    k.setLastUsedAt(Instant.now());
                    apiKeyRepository.save(k);
                    return k.getUser();
                });
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("No user found with email: " + email, HttpStatus.NOT_FOUND));
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM - this can't actually happen.
            throw new IllegalStateException(e);
        }
    }
}
