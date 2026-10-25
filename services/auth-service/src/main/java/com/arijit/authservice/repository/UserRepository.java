package com.arijit.authservice.repository;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    Page<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(String email, String name, Pageable pageable);
}
