package com.arijit.cacheservice.dto;

import jakarta.validation.constraints.NotBlank;

public record HashFieldRequest(
        @NotBlank(message = "field is required") String field,
        Object value,
        SerializationFormat serialization
) {
    public SerializationFormat serializationOrDefault(SerializationFormat fallback) {
        return serialization != null ? serialization : fallback;
    }
}
