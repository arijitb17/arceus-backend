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

    /**
     * The one query JobPollerService runs every poll interval:
     * SCHEDULED-or-RETRYING jobs due now, highest priority (then oldest
     * nextFireAt) first, capped to a batch. {@code PESSIMISTIC_WRITE} with
     * Postgres row locking is what keeps this safe if this ever runs from
     * more than one instance before real leader election exists - a second
     * instance's identical query simply blocks on (and then skips, having
     * re-read the now-RUNNING status) rows the first already claimed,
     * rather than double-dispatching the same job.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT j FROM Job j
            WHERE j.status IN (com.arijit.schedulerservice.entity.JobStatus.SCHEDULED, com.arijit.schedulerservice.entity.JobStatus.RETRYING)
            AND j.nextFireAt <= :now
            ORDER BY j.priority DESC, j.nextFireAt ASC
            """)
    List<Job> findDueJobs(@Param("now") Instant now, Pageable pageable);

    /** Jobs sitting PENDING/SCHEDULED past their own expiresAt without ever firing - picked up by JobExpiryService. */
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
