package com.arijit.cacheservice.serialization;

import com.arijit.cacheservice.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Stores the JSON text verbatim as UTF-8 bytes. Validates it actually
 * parses as JSON on the way in (catches malformed payloads at write time
 * rather than surfacing a confusing error on the next read), but stores
 * the caller's original text rather than a re-serialized/normalized form,
 * so formatting/field order round-trips exactly.
 */
@Component
@RequiredArgsConstructor
public class JsonCacheSerializer implements CacheSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public byte[] toBytes(String value) {
        try {
            objectMapper.readTree(value);
        } catch (IOException e) {
            throw new ApiException("value is not valid JSON: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String fromBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
