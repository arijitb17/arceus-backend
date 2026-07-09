package com.arijit.cacheservice.dto;

import java.util.List;

public record BulkPutResponse(List<BulkPutResult> results) {
    public record BulkPutResult(String namespace, String key, boolean success, String error) {
    }
}
