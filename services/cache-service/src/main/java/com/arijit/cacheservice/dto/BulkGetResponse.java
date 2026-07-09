package com.arijit.cacheservice.dto;

import java.util.List;

public record BulkGetResponse(List<CacheResponse> found, List<CacheKeyRef> missing) {
}
