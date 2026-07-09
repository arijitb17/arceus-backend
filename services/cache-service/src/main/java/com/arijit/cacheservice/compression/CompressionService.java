package com.arijit.cacheservice.compression;

import com.arijit.cacheservice.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Optional gzip compression for large objects. Decisions about *whether*
 * to compress a given value (explicit flag vs. size-based auto-threshold)
 * live in {@link com.arijit.cacheservice.service.CacheStoreService}; this
 * class only does the actual byte-shuffling.
 */
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
            throw new ApiException("failed to compress value: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public byte[] decompress(byte[] data) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new ApiException("failed to decompress stored value: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
