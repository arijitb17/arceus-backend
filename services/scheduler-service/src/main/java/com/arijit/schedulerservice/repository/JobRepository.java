package com.arijit.schedulerservice.repository;

import com.arijit.schedulerservice.entity.Job;
import com.arijit.schedulerservice.entity.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM Job j
            WHERE j.status IN (com.arijit.schedulerservice.entity.JobStatus.SCHEDULED, com.arijit.schedulerservice.entity.JobStatus.RETRYING)
            AND j.nextFireAt <= :now
            ORDER BY j.priority DESC, j.nextFireAt ASC
            """)
    List<Job> findDueJobs(@Param("now") Instant now, Pageable pageable);

    @Query("""
            SELECT j FROM Job j
            WHERE j.status IN (com.arijit.schedulerservice.entity.JobStatus.PENDING, com.arijit.schedulerservice.entity.JobStatus.SCHEDULED, com.arijit.schedulerservice.entity.JobStatus.PAUSED)
            AND j.expiresAt IS NOT NULL AND j.expiresAt <= :now
            """)
    List<Job> findExpired(@Param("now") Instant now);

    Page<Job> findByServiceNameAndStatus(String serviceName, JobStatus status, Pageable pageable);

    Page<Job> findByServiceName(String serviceName, Pageable pageable);

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    long countByStatus(JobStatus status);
}
