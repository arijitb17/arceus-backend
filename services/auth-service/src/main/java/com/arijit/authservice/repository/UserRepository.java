package com.arijit.authservice.repository;

import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.User;
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
}
