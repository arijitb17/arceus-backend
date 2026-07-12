package com.arijit.authservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// Deliberately not just returning Spring's own Page<T> from the controller -
// PageImpl's default JSON shape includes internal Pageable/Sort junk that's
// noisy for API consumers and not something we want to commit to as a
// public contract. This is the stable subset callers actually need.
public record UserPageResponse(
        List<AdminUserResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static UserPageResponse from(Page<AdminUserResponse> page) {
        return new UserPageResponse(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages()
        );
    }
}
