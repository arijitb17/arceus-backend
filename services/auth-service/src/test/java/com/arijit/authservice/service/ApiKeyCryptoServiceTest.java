package com.arijit.authservice.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCryptoServiceTest {

    private static String keyOf(int bytes) {
        byte[] b = new byte[bytes];
        for (int i = 0; i < bytes; i++) b[i] = (byte) (i + 1);
        return Base64.getEncoder().encodeToString(b);
    }

    private final ApiKeyCryptoService crypto = new ApiKeyCryptoService(keyOf(32));

    @Test
    void encryptDecryptRoundTrip() {
        String enc = crypto.encrypt("ak_live_supersecret");

        assertThat(enc).isNotEqualTo("ak_live_supersecret");
        assertThat(crypto.decrypt(enc)).isEqualTo("ak_live_supersecret");
    }

    @Test
    void roundTripsUnicodeAndEmpty() {
        assertThat(crypto.decrypt(crypto.encrypt("pïkâchu ⚡"))).isEqualTo("pïkâchu ⚡");
        assertThat(crypto.decrypt(crypto.encrypt(""))).isEmpty();
    }

    @Test
    void sameInputProducesDifferentCiphertextDueToRandomIv() {
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }

    @Test
    void decryptWithDifferentKeyFails() {
        String enc = crypto.encrypt("secret");
        byte[] other = new byte[32];
        ApiKeyCryptoService otherCrypto = new ApiKeyCryptoService(Base64.getEncoder().encodeToString(other));

        assertThatThrownBy(() -> otherCrypto.decrypt(enc)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedCiphertextFailsAuthentication() {
        byte[] raw = Base64.getDecoder().decode(crypto.encrypt("secret"));
        raw[raw.length - 1] ^= 0x01;

        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(raw)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void garbageInputFailsCleanly() {
        assertThatThrownBy(() -> crypto.decrypt("%%%not-base64%%%")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(new byte[3])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeyThatIsNot32Bytes() {
        assertThatThrownBy(() -> new ApiKeyCryptoService(keyOf(16)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
