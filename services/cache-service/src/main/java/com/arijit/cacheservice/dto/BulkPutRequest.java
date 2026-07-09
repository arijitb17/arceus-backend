package com.arijit.cacheservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkPutRequest(@NotEmpty(message = "entries must not be empty") @Valid List<CacheRequest> entries) {
}
