package com.arijit.authservice.repository;

import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);
    List<ApiKey> findByUserOrderByCreatedAtDesc(User user);
    Optional<ApiKey> findByIdAndUser(UUID id, User user);

    @Query("select k from ApiKey k join fetch k.user where k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHashFetchUser(@Param("keyHash") String keyHash);

    List<ApiKey> findByProjectIdAndEnvironmentOrderByCreatedAtDesc(UUID projectId, String environment);
    Optional<ApiKey> findByIdAndProjectId(UUID id, UUID projectId);
}
