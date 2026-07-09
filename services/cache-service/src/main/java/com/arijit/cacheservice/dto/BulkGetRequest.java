package com.arijit.cacheservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkGetRequest(@NotEmpty(message = "keys must not be empty") @Valid List<CacheKeyRef> keys) {
}
