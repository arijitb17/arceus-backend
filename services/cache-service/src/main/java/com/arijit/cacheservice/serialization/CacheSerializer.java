package com.arijit.cacheservice.serialization;

public interface CacheSerializer {
    byte[] toBytes(String value);

    String fromBytes(byte[] bytes);
}
