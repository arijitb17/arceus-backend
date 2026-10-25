package com.arijit.cacheservice.core;

import org.springframework.stereotype.Component;

@Component
public class CacheKeyBuilder {
    private static final String META_SUFFIX = ":__meta";

    public String dataKey(String namespace, String key) {
        return namespace + ":" + key;
    }

    public String metaKey(String namespace, String key) {
        return dataKey(namespace, key) + META_SUFFIX;
    }

    public String namespacePrefix(String namespace) {
        return namespace + ":*";
    }
}
