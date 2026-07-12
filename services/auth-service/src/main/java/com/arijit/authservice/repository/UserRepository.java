package com.arijit.authservice.repository;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// Spring Data JPA writes the implementation of this interface for you at
// runtime, based on the method names. findByEmail() becomes
// "SELECT * FROM users WHERE email = ?" automatically - no SQL, no @Query needed.
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    // Backs the admin user-search endpoint. Matches on name OR email,
    // case-insensitive substring - good enough for an admin console; swap
    // for a proper search index if this table ever gets huge.
    Page<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(String email, String name, Pageable pageable);
}
