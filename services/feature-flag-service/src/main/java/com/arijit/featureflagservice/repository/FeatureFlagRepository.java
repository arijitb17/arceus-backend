package com.arijit.featureflagservice.repository;

import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByFlagKeyAndEnvironment(String flagKey, Environment environment);

    List<FeatureFlag> findByFlagKey(String flagKey);

    Page<FeatureFlag> findByEnvironment(Environment environment, Pageable pageable);

    Page<FeatureFlag> findByStatus(FlagStatus status, Pageable pageable);

    Page<FeatureFlag> findByEnvironmentAndStatus(Environment environment, FlagStatus status, Pageable pageable);

    /** Flags with a still-pending scheduled activation/deactivation past due - safety-net sweep, see FlagScheduleSweepService. */
    List<FeatureFlag> findByActivateAtIsNotNullAndActivateAtLessThanEqualAndStatus(Instant now, FlagStatus status);

    List<FeatureFlag> findByDeactivateAtIsNotNullAndDeactivateAtLessThanEqualAndStatus(Instant now, FlagStatus status);

    boolean existsByFlagKeyAndEnvironment(String flagKey, Environment environment);
}
