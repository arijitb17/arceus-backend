package com.arijit.cacheservice.dto;

import jakarta.validation.constraints.NotBlank;

/** A bare namespace+key pointer, used by bulk-get and by gRPC CacheKeyRef. */
public record CacheKeyRef(
        @NotBlank(message = "namespace is required") String namespace,
        @NotBlank(message = "key is required") String key
) {
}
