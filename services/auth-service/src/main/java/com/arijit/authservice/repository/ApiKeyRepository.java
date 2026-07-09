package com.arijit.authservice.repository;

import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUserOrderByCreatedAtDesc(User user);
    Optional<ApiKey> findByIdAndUser(UUID id, User user);
}
