package com.arijit.featureflagservice.repository;

import com.arijit.featureflagservice.entity.FlagVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FlagVersionRepository extends JpaRepository<FlagVersion, UUID> {
    Page<FlagVersion> findByFlagIdOrderByVersionNumberDesc(UUID flagId, Pageable pageable);

    Page<FlagVersion> findByFlagKeyOrderByVersionNumberDesc(String flagKey, Pageable pageable);
}
