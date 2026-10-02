package com.arijit.organizationservice.repository;

import com.arijit.organizationservice.entity.GithubConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, UUID> {
    Optional<GithubConnection> findByProjectIdAndDisconnectedAtIsNull(UUID projectId);
}
