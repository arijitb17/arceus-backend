package com.arijit.cacheservice.serialization;

import com.arijit.cacheservice.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
