package com.arijit.authservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption for {@link com.arijit.authservice.entity.ApiKey#encryptedKey}.
 * <p>
 * <b>Why this exists at all:</b> API keys are normally hash-only - see
 * {@code ApiKeyService.create}'s original javadoc, still accurate for
 * {@code keyHash}, which is what actually authenticates a request. This
 * additional encrypted copy exists purely so a key can be shown again
 * after the creation dialog closes, at the person's explicit request,
 * fully aware that means whoever holds {@code AUTH_API_KEY_ENCRYPTION_KEY}
 * can decrypt every key ever issued - a real reduction in the guarantee
 * "even a full database dump can't be turned back into working keys" that
 * hash-only storage provides. That's a reasonable trade for a personal/
 * local project; it would not be for a real multi-tenant product.
 * <p>
 * Mirrors organization-service's SecretCryptoService exactly (see that
 * class's javadoc for the AES-GCM/IV-prefix scheme itself) - deliberately
 * copied rather than shared, since these are two different services with
 * two different KEKs and no reason to couple them through a shared
 * dependency for one small class.
 */
@Component
public class ApiKeyCryptoService {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private final SecretKeySpec kek;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyCryptoService(@Value("${arceus.auth.api-key.encryption-key}") String base64Kek) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Kek);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "arceus.auth.api-key.encryption-key must decode to exactly 32 bytes (AES-256); got " + keyBytes.length);
        }
        this.kek = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("API key encryption failed", e);
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
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("API key decryption failed", e);
        }
    }
}
