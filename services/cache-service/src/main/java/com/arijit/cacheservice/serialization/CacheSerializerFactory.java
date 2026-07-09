package com.arijit.cacheservice.serialization;

import com.arijit.cacheservice.dto.SerializationFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CacheSerializerFactory {

    private final JsonCacheSerializer jsonCacheSerializer;
    private final StringCacheSerializer stringCacheSerializer;
    private final BinaryCacheSerializer binaryCacheSerializer;

    public CacheSerializer get(SerializationFormat format) {
        return switch (format) {
            case JSON -> jsonCacheSerializer;
            case STRING -> stringCacheSerializer;
            case BINARY -> binaryCacheSerializer;
        };
    }
}
