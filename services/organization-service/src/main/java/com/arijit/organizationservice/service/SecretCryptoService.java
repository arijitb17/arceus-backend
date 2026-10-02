package com.arijit.organizationservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for {@link com.arijit.organizationservice.entity.Secret#encryptedValue}.
 * <p>
 * <b>v1 scope note</b> (see the architecture design's §8 and §12 "what's
 * left"): this encrypts every secret directly under one platform-wide KEK
 * ({@code arceus.organization.secrets.kek}, base64 AES-256 key, supplied
 * the same way auth-service supplies {@code JWT_SECRET} - an env var, not
 * stored in the DB). The design doc describes a fuller envelope-encryption
 * scheme (a distinct Data Encryption Key per project+environment, itself
 * wrapped by this KEK) so a KEK rotation doesn't require re-encrypting
 * every secret in the table one at a time - that per-project DEK layer is
 * intentionally deferred rather than half-built here; {@code
 * encryptionKeyVersion} already exists on the entity as the seam that
 * layer will slot into ("kek-v1" today, a DEK id once it exists).
 */
@Component
public class SecretCryptoService {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final String KEY_VERSION = "kek-v1";

    private final SecretKeySpec kek;
    private final SecureRandom random = new SecureRandom();

    public SecretCryptoService(@Value("${arceus.organization.secrets.kek}") String base64Kek) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Kek);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "arceus.organization.secrets.kek must decode to exactly 32 bytes (AES-256); got " + keyBytes.length);
        }
        this.kek = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Secret encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Secret decryption failed", e);
        }
    }

    public String currentKeyVersion() {
        return KEY_VERSION;
    }
}
