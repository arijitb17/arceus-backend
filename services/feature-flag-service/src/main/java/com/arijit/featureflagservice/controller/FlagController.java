package com.arijit.featureflagservice.controller;

import com.arijit.featureflagservice.dto.CreateFlagRequest;
import com.arijit.featureflagservice.dto.FlagHistoryResponse;
import com.arijit.featureflagservice.dto.FlagResponse;
import com.arijit.featureflagservice.dto.PagedResponse;
import com.arijit.featureflagservice.dto.UpdateFlagRequest;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.repository.FlagAuditLogRepository;
import com.arijit.featureflagservice.service.FlagService;
import com.arijit.featureflagservice.service.FlagValueCodec;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST surface matching the spec's endpoint list exactly:
 * {@code POST/GET/GET-by-key/PUT/DELETE /flags}, {@code POST
 * /flags/{key}/enable}, {@code POST /flags/{key}/disable}, {@code GET
 * /flags/history}. Since a key may have up to one row per {@link
 * Environment} (see FeatureFlag's Javadoc), every per-key operation takes
 * an {@code environment} query parameter, defaulting to {@code GLOBAL}
 * for the common "one flag, one value everywhere" case so the common path
 * doesn't need the query parameter at all.
 * <p>
 * Every method here is a thin adapter over {@link FlagService} - no
 * business logic lives in this class, which is also what keeps
 * FeatureFlagGrpcServiceImpl's behavior identical to this REST surface
 * for the same operations.
 */
@RestController
@RequestMapping("/flags")
@RequiredArgsConstructor
public class FlagController {

    private final FlagService flagService;
    private final FlagValueCodec valueCodec;
    private final FlagAuditLogRepository auditLogRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FlagResponse> create(@Valid @RequestBody CreateFlagRequest request) {
        FeatureFlag flag = flagService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(flag));
    }

    @GetMapping
    public PagedResponse<FlagResponse> list(
            @RequestParam(required = false) Environment environment,
            @RequestParam(required = false) FlagStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<FlagResponse> responses = flagService.list(environment, status, pageable).map(this::toResponse);
        return PagedResponse.of(responses);
    }

    @GetMapping("/{key}")
    public FlagResponse get(@PathVariable String key,
                             @RequestParam(defaultValue = "GLOBAL") Environment environment) {
        return toResponse(flagService.getOrThrow(key, environment));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public FlagResponse update(@PathVariable String key,
                                @RequestParam(defaultValue = "GLOBAL") Environment environment,
                                @Valid @RequestBody UpdateFlagRequest request) {
        return toResponse(flagService.update(key, environment, request));
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String key,
                                        @RequestParam(defaultValue = "GLOBAL") Environment environment,
                                        @RequestParam(required = false) String actor) {
        flagService.delete(key, environment, actor);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{key}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public FlagResponse enable(@PathVariable String key,
                                @RequestParam(defaultValue = "GLOBAL") Environment environment,
                                @RequestParam(required = false) String actor) {
        return toResponse(flagService.enable(key, environment, actor));
    }

    @PostMapping("/{key}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public FlagResponse disable(@PathVariable String key,
                                 @RequestParam(defaultValue = "GLOBAL") Environment environment,
                                 @RequestParam(required = false) String actor) {
        return toResponse(flagService.disable(key, environment, actor));
    }

    @GetMapping("/history")
    @PreAuthorize("hasRole('ADMIN')")
    public PagedResponse<FlagHistoryResponse> history(
            @RequestParam(required = false) String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        var entries = (key == null || key.isBlank())
                ? auditLogRepository.findAllByOrderByTimestampDesc(pageable)
                : auditLogRepository.findByFlagKeyOrderByTimestampDesc(key, pageable);
        return PagedResponse.of(entries.map(FlagHistoryResponse::fromAudit));
    }

    private FlagResponse toResponse(FeatureFlag flag) {
        return FlagResponse.from(flag, valueCodec.read(flag));
    }
}
