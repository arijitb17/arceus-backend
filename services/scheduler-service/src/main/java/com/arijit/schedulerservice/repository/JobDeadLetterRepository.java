package com.arijit.schedulerservice.repository;

import com.arijit.schedulerservice.entity.JobDeadLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JobDeadLetterRepository extends JpaRepository<JobDeadLetter, UUID> {
    Page<JobDeadLetter> findByServiceName(String serviceName, Pageable pageable);
}
