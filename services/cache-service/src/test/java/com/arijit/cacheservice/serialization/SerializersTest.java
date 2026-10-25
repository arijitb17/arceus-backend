package com.arijit.cacheservice.serialization;

import com.arijit.cacheservice.dto.SerializationFormat;
import com.arijit.cacheservice.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerializersTest {

    private final JsonCacheSerializer json = new JsonCacheSerializer(new ObjectMapper());
    private final StringCacheSerializer string = new StringCacheSerializer();
    private final BinaryCacheSerializer binary = new BinaryCacheSerializer();

    @Test
    void jsonAcceptsValidDocumentsUnchanged() {
        String doc = "{\"a\":[1,2,{\"b\":null}]}";

        assertThat(new String(json.toBytes(doc), StandardCharsets.UTF_8)).isEqualTo(doc);
        assertThat(json.fromBytes(doc.getBytes(StandardCharsets.UTF_8))).isEqualTo(doc);
    }

    @Test
    void jsonAcceptsScalars() {
        assertThat(json.toBytes("42")).isNotEmpty();
        assertThat(json.toBytes("\"text\"")).isNotEmpty();
    }

    @Test
    void jsonRejectsInvalidDocuments() {
        assertThatThrownBy(() -> json.toBytes("{not json"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("not valid JSON");
                });
    }

    @Test
    void stringIsUtf8RoundTrip() {
        String s = "héllo ⚡ 世界";

        assertThat(string.fromBytes(string.toBytes(s))).isEqualTo(s);
    }

    @Test
    void binaryDecodesBase64OnWriteAndEncodesOnRead() {
        byte[] raw = {0, 1, 2, (byte) 0xFF};
        String b64 = java.util.Base64.getEncoder().encodeToString(raw);

        assertThat(binary.toBytes(b64)).isEqualTo(raw);
        assertThat(binary.fromBytes(raw)).isEqualTo(b64);
    }

    @Test
    void binaryRejectsInvalidBase64() {
        assertThatThrownBy(() -> binary.toBytes("***not base64***"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void factorySelectsByFormat() {
        CacheSerializerFactory factory = new CacheSerializerFactory(json, string, binary);

        assertThat(factory.get(SerializationFormat.JSON)).isSameAs(json);
        assertThat(factory.get(SerializationFormat.STRING)).isSameAs(string);
        assertThat(factory.get(SerializationFormat.BINARY)).isSameAs(binary);
    }
}
