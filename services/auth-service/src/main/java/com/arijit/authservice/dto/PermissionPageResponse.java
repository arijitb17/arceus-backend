package com.arijit.authservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PermissionPageResponse(
        List<PermissionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static PermissionPageResponse from(Page<PermissionResponse> page) {
        return new PermissionPageResponse(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()
        );
    }
}
