package com.arijit.cacheservice.serialization;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class StringCacheSerializer implements CacheSerializer {
    @Override
    public byte[] toBytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String fromBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
