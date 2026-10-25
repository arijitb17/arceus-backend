package com.arijit.authservice.repository;

import com.arijit.authservice.entity.RefreshToken;
import com.arijit.authservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    void deleteByUser(User user);

    Page<RefreshToken> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Optional<RefreshToken> findByIdAndUser(UUID id, User user);

    @Modifying
    @Query("delete from RefreshToken t where t.expiryDate < :cutoff")
    int deleteByExpiryDateBefore(Instant cutoff);
}
