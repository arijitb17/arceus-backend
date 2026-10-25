package com.arijit.cacheservice.controller;

import com.arijit.cacheservice.dto.*;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.service.CacheEvictionService;
import com.arijit.cacheservice.service.CacheStatsService;
import com.arijit.cacheservice.service.CacheStoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
@Tag(name = "Cache", description = "Namespaced distributed cache: Set/Get/Delete/Update, TTL, increment, hash fields, bulk ops, eviction, stats")
public class CacheController {
    private final CacheStoreService cacheStoreService;
    private final CacheEvictionService cacheEvictionService;
    private final CacheStatsService cacheStatsService;

    @PostMapping
    @Operation(summary = "Create or overwrite a cache entry (Set Value)")
    public ResponseEntity<CacheResponse> set(@Valid @RequestBody CacheRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cacheStoreService.set(request));
    }

    @GetMapping("/{namespace}/{key}")
    @Operation(summary = "Get a cache entry")
    public ResponseEntity<CacheResponse> get(@PathVariable String namespace, @PathVariable String key) {
        return cacheStoreService.get(namespace, key)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> notFound(namespace, key));
    }

    @PutMapping("/{namespace}/{key}")
    @Operation(summary = "Update an existing cache entry (404 if it doesn't exist)")
    public ResponseEntity<CacheResponse> update(@PathVariable String namespace, @PathVariable String key,
                                                 @Valid @RequestBody CacheRequest request) {
        return ResponseEntity.ok(cacheStoreService.update(request.withNamespaceAndKey(namespace, key)));
    }

    @DeleteMapping("/{namespace}/{key}")
    @Operation(summary = "Delete a single cache entry")
    public ResponseEntity<Void> delete(@PathVariable String namespace, @PathVariable String key) {
        boolean deleted = cacheStoreService.delete(namespace, key);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/{namespace}/{key}/exists")
    @Operation(summary = "Check whether a key exists")
    public ResponseEntity<ExistsResponse> exists(@PathVariable String namespace, @PathVariable String key) {
        return ResponseEntity.ok(new ExistsResponse(namespace, key, cacheStoreService.exists(namespace, key)));
    }

    @GetMapping("/{namespace}/{key}/ttl")
    @Operation(summary = "Get remaining TTL in seconds (-1 = no expiry, -2 = not found)")
    public ResponseEntity<TtlResponse> ttl(@PathVariable String namespace, @PathVariable String key) {
        return ResponseEntity.ok(new TtlResponse(namespace, key, cacheStoreService.ttl(namespace, key)));
    }

    @PostMapping("/{namespace}/{key}/expire")
    @Operation(summary = "Set/replace the TTL on an existing key")
    public ResponseEntity<Void> expire(@PathVariable String namespace, @PathVariable String key,
                                        @Valid @RequestBody ExpireRequest request) {
        boolean applied = cacheStoreService.expire(namespace, key, request.ttlSeconds());
        return applied ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{namespace}/{key}/increment")
    @Operation(summary = "Atomically increment a numeric STRING entry (creates it at `delta` if absent)")
    public ResponseEntity<NumericResponse> increment(@PathVariable String namespace, @PathVariable String key,
                                                       @RequestBody(required = false) IncrementRequest request) {
        long delta = request != null ? request.deltaOrDefault(1) : 1;
        long value = cacheStoreService.incrementBy(namespace, key, delta);
        return ResponseEntity.ok(new NumericResponse(namespace, key, value));
    }

    @PostMapping("/{namespace}/{key}/decrement")
    @Operation(summary = "Atomically decrement a numeric STRING entry")
    public ResponseEntity<NumericResponse> decrement(@PathVariable String namespace, @PathVariable String key,
                                                       @RequestBody(required = false) IncrementRequest request) {
        long delta = request != null ? request.deltaOrDefault(1) : 1;
        long value = cacheStoreService.incrementBy(namespace, key, -delta);
        return ResponseEntity.ok(new NumericResponse(namespace, key, value));
    }

    @PutMapping("/{namespace}/{key}/hash")
    @Operation(summary = "Set a single field on a HASH entry")
    public ResponseEntity<Void> hashSet(@PathVariable String namespace, @PathVariable String key,
                                         @Valid @RequestBody HashFieldRequest request) {
        cacheStoreService.hashSetField(namespace, key, request.field(), request.value(),
                request.serializationOrDefault(SerializationFormat.JSON));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{namespace}/{key}/hash/{field}")
    @Operation(summary = "Get a single field from a HASH entry")
    public ResponseEntity<HashFieldResponse> hashGet(@PathVariable String namespace, @PathVariable String key,
                                                      @PathVariable String field) {
        return cacheStoreService.hashGetField(namespace, key, field)
                .map(value -> ResponseEntity.ok(new HashFieldResponse(namespace, key, field, true, value)))
                .orElseGet(() -> ResponseEntity.ok(new HashFieldResponse(namespace, key, field, false, null)));
    }

    @DeleteMapping("/{namespace}/{key}/hash/{field}")
    @Operation(summary = "Delete a single field from a HASH entry")
    public ResponseEntity<Void> hashDelete(@PathVariable String namespace, @PathVariable String key,
                                            @PathVariable String field) {
        boolean deleted = cacheStoreService.hashDeleteField(namespace, key, field);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/bulk/get")
    @Operation(summary = "Fetch multiple keys (across namespaces) in one call")
    public ResponseEntity<BulkGetResponse> bulkGet(@Valid @RequestBody BulkGetRequest request) {
        return ResponseEntity.ok(cacheStoreService.bulkGet(request.keys()));
    }

    @PostMapping("/bulk/put")
    @Operation(summary = "Write multiple entries in one call; partial failures are reported per-entry")
    public ResponseEntity<BulkPutResponse> bulkPut(@Valid @RequestBody BulkPutRequest request) {
        return ResponseEntity.ok(cacheStoreService.bulkPut(request.entries()));
    }

    @DeleteMapping("/namespace/{namespace}")
    @Operation(summary = "Evict every key in a namespace")
    public ResponseEntity<EvictionResponse> evictNamespace(@PathVariable String namespace) {
        long count = cacheEvictionService.evictNamespace(namespace);
        return ResponseEntity.ok(new EvictionResponse("namespace:" + namespace, count));
    }

    @DeleteMapping("/pattern/{pattern}")
    @Operation(summary = "Evict keys matching a namespace-scoped glob pattern, e.g. 'session:user:*'")
    public ResponseEntity<EvictionResponse> evictPattern(@PathVariable String pattern) {
        long count = cacheEvictionService.evictPattern(pattern);
        return ResponseEntity.ok(new EvictionResponse("pattern:" + pattern, count));
    }

    @GetMapping("/stats")
    @Operation(summary = "Hit/miss/eviction counters plus live Redis-level stats")
    public ResponseEntity<StatsResponse> stats() {
        return ResponseEntity.ok(cacheStatsService.stats());
    }

    private ApiException notFound(String namespace, String key) {
        return new ApiException("no cache entry for namespace='%s' key='%s'".formatted(namespace, key), HttpStatus.NOT_FOUND);
    }
}
