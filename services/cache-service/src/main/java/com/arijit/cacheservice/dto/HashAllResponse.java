package com.arijit.cacheservice.dto;

import java.util.Map;

public record HashAllResponse(String namespace, String key, Map<String, Object> fields) {
}
