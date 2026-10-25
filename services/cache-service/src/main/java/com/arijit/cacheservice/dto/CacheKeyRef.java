package com.arijit.cacheservice.dto;

import jakarta.validation.constraints.NotBlank;

public record CacheKeyRef(
        @NotBlank(message = "namespace is required") String namespace,
        @NotBlank(message = "key is required") String key
) {
}
