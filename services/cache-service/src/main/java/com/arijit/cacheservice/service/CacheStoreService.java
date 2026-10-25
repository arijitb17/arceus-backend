package com.arijit.cacheservice.service;

import com.arijit.cacheservice.compression.CompressionService;
import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.core.CacheEntryMetadata;
import com.arijit.cacheservice.core.CacheKeyBuilder;
import com.arijit.cacheservice.core.NamespaceValidator;
import com.arijit.cacheservice.dto.*;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.serialization.CacheSerializer;
import com.arijit.cacheservice.serialization.CacheSerializerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheStoreService {
    private final RedisTemplate<String, byte[]> cacheRedisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final NamespaceValidator namespaceValidator;
    private final CacheTtlService cacheTtlService;
    private final CacheStatsService cacheStatsService;
    private final CacheEvictionService cacheEvictionService;
    private final CacheSerializerFactory serializerFactory;
    private final CompressionService compressionService;
    private final CacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    public CacheResponse set(CacheRequest request) {
        return write(request, false);
    }

    public CacheResponse update(CacheRequest request) {
        return write(request, true);
    }

    private CacheResponse write(CacheRequest request, boolean requireExisting) {
        String namespace = request.namespace();
        String key = request.key();
        namespaceValidator.validate(namespace);
        if (key == null || key.isBlank()) {
            throw new ApiException("key is required", HttpStatus.BAD_REQUEST);
        }

        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        String metaKey = cacheKeyBuilder.metaKey(namespace, key);

        if (requireExisting && !Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey))) {
            throw new ApiException("cannot update: key '%s' does not exist in namespace '%s'".formatted(key, namespace), HttpStatus.NOT_FOUND);
        }

        CacheDataType type = request.typeOrDefault();
        SerializationFormat serialization = request.serialization() != null ? request.serialization() : cacheProperties.defaultSerialization();
        TtlPolicy ttlPolicy = request.ttlPolicyOrDefault();
        Optional<Long> ttlSeconds = cacheTtlService.resolveTtlSeconds(ttlPolicy, request.ttlSeconds(), request.absoluteExpireAt());

        boolean compressed;
        switch (type) {
            case STRING -> compressed = writeString(dataKey, request.value(), serialization, request.compressOrDefault());
            case HASH -> compressed = writeHash(dataKey, request.value());
            case LIST -> compressed = writeList(dataKey, request.value());
            case SET -> compressed = writeSet(dataKey, request.value());
            case SORTED_SET -> compressed = writeSortedSet(dataKey, request.value());
            default -> throw new ApiException("unsupported type " + type, HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        Instant createdAt = now;
        if (requireExisting) {
            CacheEntryMetadata existing = readMetadata(metaKey);
            if (existing != null) {
                createdAt = existing.createdAt();
            }
        }

        CacheEntryMetadata metadata = new CacheEntryMetadata(
                type, serialization, compressed, ttlPolicy,
                ttlSeconds.orElse(0L), request.cachePolicyOrDefault(), createdAt, now);
        stringRedisTemplate.opsForHash().putAll(metaKey, metadata.toRedisHash());

        applyTtl(dataKey, metaKey, ttlSeconds);

        log.debug("cache set namespace={} key={} type={} ttlPolicy={}", namespace, key, type, ttlPolicy);
        return toResponse(namespace, key, metadata, request.value());
    }

    private boolean writeString(String dataKey, Object value, SerializationFormat serialization, boolean forceCompress) {
        String raw = valueAsString(value, serialization);
        CacheSerializer serializer = serializerFactory.get(serialization);
        byte[] bytes = serializer.toBytes(raw);
        checkSize(bytes.length);

        boolean shouldCompress = forceCompress || (cacheProperties.compression().enabled() && bytes.length >= cacheProperties.compression().autoThresholdBytes());
        byte[] toStore = shouldCompress ? compressionService.compress(bytes) : bytes;

        cacheRedisTemplate.opsForValue().set(dataKey, toStore);
        return shouldCompress;
    }

    private boolean writeHash(String dataKey, Object value) {
        Map<String, Object> map = objectMapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
        });
        if (map == null) {
            throw new ApiException("HASH type requires an object value of field -> value", HttpStatus.BAD_REQUEST);
        }
        Map<String, byte[]> encoded = new LinkedHashMap<>();
        long totalSize = 0;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            byte[] bytes = toJsonBytes(entry.getValue());
            totalSize += bytes.length;
            encoded.put(entry.getKey(), bytes);
        }
        checkSize(totalSize);

        cacheRedisTemplate.delete(dataKey);
        if (!encoded.isEmpty()) {
            cacheRedisTemplate.opsForHash().putAll(dataKey, encoded);
        }
        return false;
    }

    private boolean writeList(String dataKey, Object value) {
        List<Object> list = objectMapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {
        });
        if (list == null) {
            throw new ApiException("LIST type requires an array value", HttpStatus.BAD_REQUEST);
        }
        cacheRedisTemplate.delete(dataKey);
        long totalSize = 0;
        List<byte[]> encoded = new ArrayList<>();
        for (Object element : list) {
            byte[] bytes = toJsonBytes(element);
            totalSize += bytes.length;
            encoded.add(bytes);
        }
        checkSize(totalSize);
        if (!encoded.isEmpty()) {
            cacheRedisTemplate.opsForList().rightPushAll(dataKey, encoded);
        }
        return false;
    }

    private boolean writeSet(String dataKey, Object value) {
        List<Object> list = objectMapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {
        });
        if (list == null) {
            throw new ApiException("SET type requires an array value", HttpStatus.BAD_REQUEST);
        }
        cacheRedisTemplate.delete(dataKey);
        long totalSize = 0;
        List<byte[]> encoded = new ArrayList<>();
        for (Object element : list) {
            byte[] bytes = toJsonBytes(element);
            totalSize += bytes.length;
            encoded.add(bytes);
        }
        checkSize(totalSize);
        if (!encoded.isEmpty()) {
            cacheRedisTemplate.opsForSet().add(dataKey, encoded.toArray(new byte[0][]));
        }
        return false;
    }

    private boolean writeSortedSet(String dataKey, Object value) {
        List<Map<String, Object>> entries = objectMapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
        });
        if (entries == null) {
            throw new ApiException("SORTED_SET type requires an array of {member, score}", HttpStatus.BAD_REQUEST);
        }
        cacheRedisTemplate.delete(dataKey);
        long totalSize = 0;
        List<byte[]> encodedMembers = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if (!entry.containsKey("member") || !entry.containsKey("score")) {
                throw new ApiException("SORTED_SET entries require 'member' and 'score' fields", HttpStatus.BAD_REQUEST);
            }
            byte[] bytes = toJsonBytes(entry.get("member"));
            totalSize += bytes.length;
            encodedMembers.add(bytes);
        }
        checkSize(totalSize);
        for (int i = 0; i < entries.size(); i++) {
            double score = ((Number) entries.get(i).get("score")).doubleValue();
            cacheRedisTemplate.opsForZSet().add(dataKey, encodedMembers.get(i), score);
        }
        return false;
    }

    public Optional<CacheResponse> get(String namespace, String key) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        String metaKey = cacheKeyBuilder.metaKey(namespace, key);

        CacheEntryMetadata metadata = readMetadata(metaKey);
        if (metadata == null) {
            cacheStatsService.recordMiss(namespace, key);
            return Optional.empty();
        }

        Object value = switch (metadata.type()) {
            case STRING -> readString(dataKey, metadata);
            case HASH -> readHash(dataKey);
            case LIST -> readList(dataKey);
            case SET -> readSet(dataKey);
            case SORTED_SET -> readSortedSet(dataKey);
        };

        cacheStatsService.recordHit(namespace, key);

        if (metadata.ttlPolicy() == TtlPolicy.SLIDING) {
            applyTtl(dataKey, metaKey, Optional.of(metadata.ttlSeconds()));
        }

        long ttlRemaining = Optional.ofNullable(stringRedisTemplate.getExpire(dataKey, TimeUnit.SECONDS)).orElse(-1L);
        return Optional.of(new CacheResponse(namespace, key, value, metadata.type(), metadata.serialization(),
                metadata.ttlPolicy(), ttlRemaining, metadata.createdAt(), metadata.updatedAt()));
    }

    private Object readString(String dataKey, CacheEntryMetadata metadata) {
        byte[] stored = cacheRedisTemplate.opsForValue().get(dataKey);
        if (stored == null) {
            return null;
        }
        byte[] raw = metadata.compressed() ? compressionService.decompress(stored) : stored;
        String text = serializerFactory.get(metadata.serialization()).fromBytes(raw);
        return metadata.serialization() == SerializationFormat.JSON ? parseJsonOrRaw(text) : text;
    }

    private Map<String, Object> readHash(String dataKey) {
        Map<Object, Object> raw = cacheRedisTemplate.opsForHash().entries(dataKey);
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((field, bytes) -> result.put(String.valueOf(field), fromJsonBytes((byte[]) bytes)));
        return result;
    }

    private List<Object> readList(String dataKey) {
        List<byte[]> raw = cacheRedisTemplate.opsForList().range(dataKey, 0, -1);
        List<Object> result = new ArrayList<>();
        if (raw != null) {
            raw.forEach(bytes -> result.add(fromJsonBytes(bytes)));
        }
        return result;
    }

    private List<Object> readSet(String dataKey) {
        java.util.Set<byte[]> raw = cacheRedisTemplate.opsForSet().members(dataKey);
        List<Object> result = new ArrayList<>();
        if (raw != null) {
            raw.forEach(bytes -> result.add(fromJsonBytes(bytes)));
        }
        return result;
    }

    private List<Map<String, Object>> readSortedSet(String dataKey) {
        var raw = cacheRedisTemplate.opsForZSet().rangeWithScores(dataKey, 0, -1);
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw != null) {
            raw.forEach(tuple -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("member", fromJsonBytes(tuple.getValue()));
                entry.put("score", tuple.getScore());
                result.add(entry);
            });
        }
        return result;
    }

    public boolean delete(String namespace, String key) {
        return cacheEvictionService.evictKey(namespace, key);
    }

    public boolean exists(String namespace, String key) {
        namespaceValidator.validate(namespace);
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(cacheKeyBuilder.metaKey(namespace, key)));
    }

    public long ttl(String namespace, String key) {
        namespaceValidator.validate(namespace);
        Long ttl = stringRedisTemplate.getExpire(cacheKeyBuilder.dataKey(namespace, key), TimeUnit.SECONDS);
        return ttl != null ? ttl : -2;
    }

    public boolean expire(String namespace, String key, long ttlSeconds) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        String metaKey = cacheKeyBuilder.metaKey(namespace, key);
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(metaKey))) {
            return false;
        }
        applyTtl(dataKey, metaKey, Optional.of(ttlSeconds));
        return true;
    }

    public long incrementBy(String namespace, String key, long delta) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        String metaKey = cacheKeyBuilder.metaKey(namespace, key);

        CacheEntryMetadata existing = readMetadata(metaKey);
        if (existing != null && existing.type() != CacheDataType.STRING) {
            throw new ApiException("increment/decrement only applies to STRING entries; '%s' is %s".formatted(key, existing.type()), HttpStatus.CONFLICT);
        }

        Long result = cacheRedisTemplate.opsForValue().increment(dataKey, delta);

        if (existing == null) {
            Instant now = Instant.now();
            CacheEntryMetadata fresh = new CacheEntryMetadata(CacheDataType.STRING, SerializationFormat.STRING, false,
                    TtlPolicy.NO_EXPIRY, 0L, CachePolicy.CACHE_ASIDE, now, now);
            stringRedisTemplate.opsForHash().putAll(metaKey, fresh.toRedisHash());
        } else {
            stringRedisTemplate.opsForHash().putAll(metaKey, existing.withUpdatedNow().toRedisHash());
        }
        return result != null ? result : 0;
    }

    public void hashSetField(String namespace, String key, String field, Object value, SerializationFormat serialization) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        String metaKey = cacheKeyBuilder.metaKey(namespace, key);

        CacheEntryMetadata existing = readMetadata(metaKey);
        if (existing != null && existing.type() != CacheDataType.HASH) {
            throw new ApiException("'%s' already exists as %s, not HASH".formatted(key, existing.type()), HttpStatus.CONFLICT);
        }

        byte[] bytes = toJsonBytes(value);
        checkSize(bytes.length);
        cacheRedisTemplate.opsForHash().put(dataKey, field, bytes);

        Instant now = Instant.now();
        CacheEntryMetadata metadata = existing != null
                ? existing.withUpdatedNow()
                : new CacheEntryMetadata(CacheDataType.HASH, serialization, false, TtlPolicy.NO_EXPIRY, 0L, CachePolicy.CACHE_ASIDE, now, now);
        stringRedisTemplate.opsForHash().putAll(metaKey, metadata.toRedisHash());
    }

    public Optional<Object> hashGetField(String namespace, String key, String field) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        Object raw = cacheRedisTemplate.opsForHash().get(dataKey, field);
        if (raw == null) {
            cacheStatsService.recordMiss(namespace, key + "." + field);
            return Optional.empty();
        }
        cacheStatsService.recordHit(namespace, key + "." + field);
        return Optional.of(fromJsonBytes((byte[]) raw));
    }

    public boolean hashDeleteField(String namespace, String key, String field) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        Long deleted = cacheRedisTemplate.opsForHash().delete(dataKey, field);
        return deleted != null && deleted > 0;
    }

    public BulkGetResponse bulkGet(List<CacheKeyRef> refs) {
        List<CacheResponse> found = new ArrayList<>();
        List<CacheKeyRef> missing = new ArrayList<>();
        for (CacheKeyRef ref : refs) {
            get(ref.namespace(), ref.key()).ifPresentOrElse(found::add, () -> missing.add(ref));
        }
        return new BulkGetResponse(found, missing);
    }

    public BulkPutResponse bulkPut(List<CacheRequest> requests) {
        List<BulkPutResponse.BulkPutResult> results = new ArrayList<>();
        for (CacheRequest request : requests) {
            try {
                set(request);
                results.add(new BulkPutResponse.BulkPutResult(request.namespace(), request.key(), true, null));
            } catch (ApiException e) {
                results.add(new BulkPutResponse.BulkPutResult(request.namespace(), request.key(), false, e.getMessage()));
            } catch (Exception e) {
                log.error("Unexpected error during bulkPut for {}:{}", request.namespace(), request.key(), e);
                results.add(new BulkPutResponse.BulkPutResult(request.namespace(), request.key(), false, "internal error"));
            }
        }
        return new BulkPutResponse(results);
    }

    private void applyTtl(String dataKey, String metaKey, Optional<Long> ttlSeconds) {
        if (ttlSeconds.isEmpty()) {
            stringRedisTemplate.persist(dataKey);
            stringRedisTemplate.persist(metaKey);
            return;
        }
        long seconds = ttlSeconds.get();
        stringRedisTemplate.expire(dataKey, seconds, TimeUnit.SECONDS);
        stringRedisTemplate.expire(metaKey, seconds, TimeUnit.SECONDS);
    }

    private CacheEntryMetadata readMetadata(String metaKey) {
        Map<Object, Object> hash = stringRedisTemplate.opsForHash().entries(metaKey);
        return CacheEntryMetadata.fromRedisHash(hash);
    }

    private void checkSize(long bytes) {
        if (bytes > cacheProperties.maxObjectSizeBytes()) {
            throw new ApiException(
                    "value size %d bytes exceeds maximum-object-size %d bytes".formatted(bytes, cacheProperties.maxObjectSizeBytes()),
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }
    }

    private String valueAsString(Object value, SerializationFormat serialization) {
        if (value == null) {
            throw new ApiException("value is required", HttpStatus.BAD_REQUEST);
        }
        if (serialization == SerializationFormat.JSON) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new ApiException("value could not be serialized to JSON: " + e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }
        if (value instanceof String s) {
            return s;
        }
        throw new ApiException("%s serialization requires a plain string value".formatted(serialization), HttpStatus.BAD_REQUEST);
    }

    private byte[] toJsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApiException("value could not be serialized to JSON: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private Object fromJsonBytes(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return parseJsonOrRaw(new String(bytes, StandardCharsets.UTF_8));
    }

    private Object parseJsonOrRaw(String text) {
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception e) {
            return text;
        }
    }

    private CacheResponse toResponse(String namespace, String key, CacheEntryMetadata metadata, Object originalValue) {
        long ttlRemaining = Optional.ofNullable(stringRedisTemplate.getExpire(cacheKeyBuilder.dataKey(namespace, key), TimeUnit.SECONDS)).orElse(-1L);
        return new CacheResponse(namespace, key, originalValue, metadata.type(), metadata.serialization(),
                metadata.ttlPolicy(), ttlRemaining, metadata.createdAt(), metadata.updatedAt());
    }
}
