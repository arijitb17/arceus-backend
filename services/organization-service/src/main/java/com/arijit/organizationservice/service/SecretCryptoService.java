package com.arijit.organizationservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
            log.error("Secret encryption failed", e);
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
            log.error("Secret decryption failed - wrong key version or corrupted ciphertext?", e);
            throw new IllegalStateException("Secret decryption failed", e);
        }
    }

    public String currentKeyVersion() {
        return KEY_VERSION;
    }
}
