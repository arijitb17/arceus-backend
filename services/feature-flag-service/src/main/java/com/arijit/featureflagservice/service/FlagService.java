package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.dto.CreateFlagRequest;
import com.arijit.featureflagservice.dto.EvaluationResult;
import com.arijit.featureflagservice.dto.UpdateFlagRequest;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import com.arijit.featureflagservice.entity.FlagVersion;
import com.arijit.featureflagservice.entity.TargetType;
import com.arijit.featureflagservice.exception.ApiException;
import com.arijit.featureflagservice.messaging.FlagEventPublisher;
import com.arijit.featureflagservice.repository.FeatureFlagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Everything a caller (REST controller, gRPC service impl, or the
 * internal scheduler-callback controller - all thin adapters over this
 * class, so every entry point gets identical validation and behavior)
 * can do to a flag: create, read, update, delete, enable, disable,
 * evaluate. Every state-changing method here, in the same transaction:
 * (1) validates, (2) mutates and saves the row, (3) records a
 * {@link FlagVersion} snapshot, (4) records a local audit entry, (5)
 * evicts the cache entry, then, outside the transaction's critical path
 * but still synchronously, (6) publishes a domain event. Scheduling
 * side-effects (registering/cancelling scheduler-service jobs) happen
 * last since they're the most likely to fail transiently and must never
 * block the flag mutation itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlagService {

    private final FeatureFlagRepository flagRepository;
    private final FlagValidationService validationService;
    private final FlagValueCodec valueCodec;
    private final FlagVersionService versionService;
    private final FlagAuditService auditService;
    private final FlagCacheService cacheService;
    private final FlagEventPublisher eventPublisher;
    private final FlagSchedulingService schedulingService;
    private final RolloutEvaluator rolloutEvaluator;
    private final ObjectMapper objectMapper;

    @Transactional
    public FeatureFlag create(CreateFlagRequest request) {
        validationService.validateCreate(request);

        TargetType targetType = request.targetType() != null ? request.targetType() : TargetType.ENVIRONMENT;
        Environment environment = targetType == TargetType.GLOBAL ? Environment.GLOBAL : request.environment();
        String actor = actorOrUnknown(request.actor());

        if (flagRepository.existsByFlagKeyAndEnvironment(request.key(), environment)) {
            throw new ApiException("Flag '" + request.key() + "' already exists for environment " + environment, HttpStatus.CONFLICT);
        }

        Instant now = Instant.now();
        FeatureFlag flag = FeatureFlag.builder()
                .flagKey(request.key())
                .name(request.name())
                .description(request.description())
                .type(request.type())
                .environment(environment)
                .targetType(targetType)
                .status(FlagStatus.DISABLED)
                .rolloutPercentage(request.rolloutPercentage() != null ? request.rolloutPercentage() : 100)
                .activateAt(request.activateAt())
                .deactivateAt(request.deactivateAt())
                .createdBy(actor)
                .updatedBy(actor)
                .createdAt(now)
                .updatedAt(now)
                .build();
        valueCodec.write(flag, request.type(), request.value());

        flag = flagRepository.save(flag);
        applySchedule(flag);
        flag = flagRepository.save(flag);

        versionService.record(flag, FlagVersion.ChangeType.CREATE, actor);
        auditService.record(flag.getId(), flag.getFlagKey(), flag.getEnvironment(), "CREATE", actor, null, valueCodec.toJsonString(flag));
        eventPublisher.created(flag, actor);
        log.info("Created flag {}/{}", flag.getFlagKey(), flag.getEnvironment());
        return flag;
    }

    @Transactional(readOnly = true)
    public FeatureFlag getOrThrow(String key, Environment environment) {
        return flagRepository.findByFlagKeyAndEnvironment(key, environment)
                .orElseThrow(() -> new ApiException("No flag '" + key + "' for environment " + environment, HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<FeatureFlag> list(Environment environment, FlagStatus status, Pageable pageable) {
        if (environment != null && status != null) {
            return flagRepository.findByEnvironmentAndStatus(environment, status, pageable);
        } else if (environment != null) {
            return flagRepository.findByEnvironment(environment, pageable);
        } else if (status != null) {
            return flagRepository.findByStatus(status, pageable);
        }
        return flagRepository.findAll(pageable);
    }

    @Transactional
    public FeatureFlag update(String key, Environment environment, UpdateFlagRequest request) {
        validationService.validateUpdate(request);
        FeatureFlag flag = getOrThrow(key, environment);
        String actor = actorOrUnknown(request.actor());
        String oldValue = valueCodec.toJsonString(flag);

        if (request.name() != null) flag.setName(request.name());
        if (request.description() != null) flag.setDescription(request.description());
        if (request.value() != null) valueCodec.write(flag, flag.getType(), request.value());
        if (request.rolloutPercentage() != null) flag.setRolloutPercentage(request.rolloutPercentage());

        boolean scheduleChanged = false;
        if (request.activateAt() != null && !request.activateAt().equals(flag.getActivateAt())) {
            flag.setActivateAt(request.activateAt());
            scheduleChanged = true;
        }
        if (request.deactivateAt() != null && !request.deactivateAt().equals(flag.getDeactivateAt())) {
            flag.setDeactivateAt(request.deactivateAt());
            scheduleChanged = true;
        }

        flag.setUpdatedBy(actor);
        flag.setUpdatedAt(Instant.now());
        flag = flagRepository.save(flag);

        if (scheduleChanged) {
            schedulingService.cancel(flag.getActivateJobId());
            schedulingService.cancel(flag.getDeactivateJobId());
            flag.setActivateJobId(null);
            flag.setDeactivateJobId(null);
            applySchedule(flag);
            flag = flagRepository.save(flag);
        }

        cacheService.evict(flag.getFlagKey(), flag.getEnvironment());
        versionService.record(flag, FlagVersion.ChangeType.UPDATE, actor);
        auditService.record(flag.getId(), flag.getFlagKey(), flag.getEnvironment(), "UPDATE", actor, oldValue, valueCodec.toJsonString(flag));
        eventPublisher.updated(flag, actor);
        log.info("Updated flag {}/{}", flag.getFlagKey(), flag.getEnvironment());
        return flag;
    }

    @Transactional
    public void delete(String key, Environment environment, String actorRaw) {
        FeatureFlag flag = getOrThrow(key, environment);
        String actor = actorOrUnknown(actorRaw);

        schedulingService.cancel(flag.getActivateJobId());
        schedulingService.cancel(flag.getDeactivateJobId());

        versionService.record(flag, FlagVersion.ChangeType.DELETE, actor);
        auditService.record(flag.getId(), flag.getFlagKey(), flag.getEnvironment(), "DELETE", actor, valueCodec.toJsonString(flag), null);
        cacheService.evict(flag.getFlagKey(), flag.getEnvironment());

        flagRepository.delete(flag);
        eventPublisher.deleted(flag, actor);
        log.info("Deleted flag {}/{}", key, environment);
    }

    @Transactional
    public FeatureFlag enable(String key, Environment environment, String actorRaw) {
        return setStatus(key, environment, FlagStatus.ENABLED, actorRaw);
    }

    @Transactional
    public FeatureFlag disable(String key, Environment environment, String actorRaw) {
        return setStatus(key, environment, FlagStatus.DISABLED, actorRaw);
    }

    private FeatureFlag setStatus(String key, Environment environment, FlagStatus newStatus, String actorRaw) {
        FeatureFlag flag = getOrThrow(key, environment);
        String actor = actorOrUnknown(actorRaw);
        if (flag.getStatus() == newStatus) {
            return flag;
        }
        flag.setStatus(newStatus);
        flag.setUpdatedBy(actor);
        flag.setUpdatedAt(Instant.now());
        flag = flagRepository.save(flag);

        cacheService.evict(flag.getFlagKey(), flag.getEnvironment());
        versionService.record(flag, newStatus == FlagStatus.ENABLED ? FlagVersion.ChangeType.ENABLE : FlagVersion.ChangeType.DISABLE, actor);
        auditService.record(flag.getId(), flag.getFlagKey(), flag.getEnvironment(), newStatus.name(), actor, null, null);
        if (newStatus == FlagStatus.ENABLED) {
            eventPublisher.enabled(flag, actor);
        } else {
            eventPublisher.disabled(flag, actor);
        }
        log.info("Set flag {}/{} to {}", flag.getFlagKey(), flag.getEnvironment(), newStatus);
        return flag;
    }

    /**
     * Called both from FlagScheduleCallbackController (scheduler-service's
     * HTTP_CALLBACK firing) and, redundantly but safely, could be called
     * by a future safety-net sweep - idempotent either way since setStatus
     * short-circuits when the flag is already in the target state.
     */
    @Transactional
    public FeatureFlag applyScheduledActivation(String key, Environment environment) {
        FeatureFlag flag = enable(key, environment, "scheduler-service");
        flag.setActivateJobId(null);
        return flagRepository.save(flag);
    }

    @Transactional
    public FeatureFlag applyScheduledDeactivation(String key, Environment environment) {
        FeatureFlag flag = disable(key, environment, "scheduler-service");
        flag.setDeactivateJobId(null);
        return flagRepository.save(flag);
    }

    /**
     * Resolves a flag's effective value for a caller: not found -> the
     * caller's default; DISABLED -> the caller's default; ENABLED but
     * outside the rollout bucket -> the caller's default; otherwise the
     * flag's stored value. Reads through {@link FlagCacheService} first -
     * a cache hit skips the database entirely.
     */
    @Transactional(readOnly = true)
    public EvaluationResult evaluate(String key, Environment environment, String bucketKey, Object defaultValue) {
        String cached = cacheService.get(key, environment);
        FlagCacheEntry entry;
        boolean fromCache = false;
        if (cached != null) {
            entry = deserializeCacheEntry(cached);
            fromCache = entry != null;
        } else {
            entry = null;
        }

        if (entry == null) {
            FeatureFlag flag = flagRepository.findByFlagKeyAndEnvironment(key, environment).orElse(null);
            if (flag == null) {
                return EvaluationResult.notFound(defaultValue);
            }
            entry = new FlagCacheEntry(flag.getFlagKey(), flag.getType(), flag.getStatus(),
                    flag.getRolloutPercentage(), valueCodec.toJsonString(flag));
            cacheService.put(key, environment, serializeCacheEntry(entry));
        }

        if (entry.status() != FlagStatus.ENABLED) {
            return new EvaluationResult(true, false, defaultValue, entry.type(), fromCache);
        }
        if (!rolloutEvaluator.isIncluded(entry.rolloutPercentage(), entry.flagKey(), bucketKey)) {
            return new EvaluationResult(true, false, defaultValue, entry.type(), fromCache);
        }
        return new EvaluationResult(true, true, valueCodec.fromJsonString(entry.valueJson(), entry.type()), entry.type(), fromCache);
    }

    private void applySchedule(FeatureFlag flag) {
        if (flag.getActivateAt() != null) {
            flag.setActivateJobId(schedulingService.scheduleActivation(flag));
        }
        if (flag.getDeactivateAt() != null) {
            flag.setDeactivateJobId(schedulingService.scheduleDeactivation(flag));
        }
    }

    private String actorOrUnknown(String actor) {
        return actor == null || actor.isBlank() ? "unknown" : actor;
    }

    // ---- cache payload is a minimal FlagCacheEntry, not the full entity - see that record's Javadoc for why ----

    private String serializeCacheEntry(FlagCacheEntry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (Exception e) {
            log.warn("Failed to serialize cache entry for flag {}: {}", entry.flagKey(), e.getMessage());
            return null;
        }
    }

    private FlagCacheEntry deserializeCacheEntry(String json) {
        try {
            return objectMapper.readValue(json, FlagCacheEntry.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize cache entry, treating as a cache miss: {}", e.getMessage());
            return null;
        }
    }
}
