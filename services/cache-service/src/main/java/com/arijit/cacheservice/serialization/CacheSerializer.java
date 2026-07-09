package com.arijit.cacheservice.serialization;

/**
 * Converts between the string representation transported in requests/
 * responses (see {@link com.arijit.cacheservice.dto.SerializationFormat})
 * and the raw bytes actually written to Redis. Compression (if any) is
 * layered on top of this by the caller, not inside these implementations -
 * keeps "how is it encoded" and "is it compressed" as two independent,
 * composable concerns.
 */
public interface CacheSerializer {
    byte[] toBytes(String value);

    String fromBytes(byte[] bytes);
}
