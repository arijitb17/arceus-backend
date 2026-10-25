package com.arijit.cacheservice.compression;

import com.arijit.cacheservice.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompressionServiceTest {

    private final CompressionService service = new CompressionService();

    @Test
    void roundTrip() {
        byte[] data = "hello hello hello hello hello".getBytes(StandardCharsets.UTF_8);

        assertThat(service.decompress(service.compress(data))).isEqualTo(data);
    }

    @Test
    void compressesRepetitiveDataSmaller() {
        byte[] data = "abcabcabc".repeat(1000).getBytes(StandardCharsets.UTF_8);

        assertThat(service.compress(data).length).isLessThan(data.length / 10);
    }

    @Test
    void emptyInputRoundTrips() {
        assertThat(service.decompress(service.compress(new byte[0]))).isEmpty();
    }

    @Test
    void decompressingNonGzipFailsWithApiException() {
        assertThatThrownBy(() -> service.decompress("plain text, not gzip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("failed to decompress");
    }
}
