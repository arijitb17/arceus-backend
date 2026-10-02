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

    /**
     * Same lookup as {@link #findByKeyHash}, but eagerly joins {@code user}
     * so the returned entity is safe to read {@code getUser()} from after
     * the transaction that loaded it has closed - used by
     * ApiKeyService.validateAndTouch, whose result crosses into
     * ApiKeyProvisioningGrpcService's non-transactional gRPC method. ApiKey.user
     * is {@code FetchType.LAZY} (right, for every other read path that
     * doesn't need it), so without this, that gRPC method's
     * {@code apiKey.getUser()} throws LazyInitializationException instead
     * of returning the already-loaded User.
     */
    @Query("select k from ApiKey k join fetch k.user where k.keyHash = :keyHash")
    Optional<ApiKey> findByKeyHashFetchUser(@Param("keyHash") String keyHash);

    // ---------- Project-scoped keys (architecture design §7) ----------
    List<ApiKey> findByProjectIdAndEnvironmentOrderByCreatedAtDesc(UUID projectId, String environment);
    Optional<ApiKey> findByIdAndProjectId(UUID id, UUID projectId);
}
