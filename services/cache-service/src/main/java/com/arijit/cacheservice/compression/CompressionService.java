package com.arijit.cacheservice.compression;

import com.arijit.cacheservice.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Component
public class CompressionService {
    public byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(byteStream)) {
                gzip.write(data);
            }
            return byteStream.toByteArray();
        } catch (IOException e) {
            log.error("Failed to compress value ({} bytes)", data.length, e);
            throw new ApiException("failed to compress value: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] decompress(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            log.error("Failed to decompress stored value ({} bytes)", data.length, e);
            throw new ApiException("failed to decompress stored value: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
