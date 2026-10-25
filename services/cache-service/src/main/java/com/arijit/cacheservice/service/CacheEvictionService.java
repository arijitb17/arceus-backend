package com.arijit.cacheservice.service;

import com.arijit.cacheservice.core.CacheKeyBuilder;
import com.arijit.cacheservice.core.NamespaceValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheEvictionService {
    private static final int SCAN_BATCH_SIZE = 500;
    private static final int DELETE_BATCH_SIZE = 500;

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheKeyBuilder cacheKeyBuilder;
    private final NamespaceValidator namespaceValidator;
    private final CacheStatsService cacheStatsService;

    public boolean evictKey(String namespace, String key) {
        namespaceValidator.validate(namespace);
        String dataKey = cacheKeyBuilder.dataKey(namespace, key);
        String metaKey = cacheKeyBuilder.metaKey(namespace, key);
        Long deleted = stringRedisTemplate.delete(List.of(dataKey, metaKey));
        boolean existed = deleted != null && deleted > 0;
        if (existed) {
            cacheStatsService.recordEvictions("manual:" + namespace, 1);
        }
        return existed;
    }

    public long evictNamespace(String namespace) {
        namespaceValidator.validate(namespace);
        long count = deleteByPattern(cacheKeyBuilder.namespacePrefix(namespace));
        cacheStatsService.recordEvictions("namespace:" + namespace, count);
        return count;
    }

    public long evictPattern(String pattern) {
        namespaceValidator.validatePatternIsNamespaceScoped(pattern);
        long count = deleteByPattern(pattern);
        cacheStatsService.recordEvictions("pattern:" + pattern, count);
        return count;
    }

    private long deleteByPattern(String pattern) {
        List<String> matched = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build();

        try (var cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                matched.add(cursor.next());
            }
        }

        long deletedCount = 0;
        for (int i = 0; i < matched.size(); i += DELETE_BATCH_SIZE) {
            List<String> batch = matched.subList(i, Math.min(i + DELETE_BATCH_SIZE, matched.size()));
            Long deleted = stringRedisTemplate.delete(batch);
            deletedCount += deleted != null ? deleted : 0;
        }

        return deletedCount;
    }
}
