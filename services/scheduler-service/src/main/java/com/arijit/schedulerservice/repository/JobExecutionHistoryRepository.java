package com.arijit.schedulerservice.repository;

import com.arijit.schedulerservice.entity.JobExecutionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobExecutionHistoryRepository extends JpaRepository<JobExecutionHistory, UUID> {
    Page<JobExecutionHistory> findByJobIdOrderByStartedAtDesc(UUID jobId, Pageable pageable);
}
