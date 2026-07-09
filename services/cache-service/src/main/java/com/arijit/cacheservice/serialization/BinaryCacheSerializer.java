package com.arijit.cacheservice.serialization;

import com.arijit.cacheservice.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Base64;

/** value is Base64 text on the wire; raw decoded bytes are what's stored in Redis. */
@Component
public class BinaryCacheSerializer implements CacheSerializer {

    @Override
    public byte[] toBytes(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException("value is not valid Base64 for BINARY serialization", HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public String fromBytes(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
