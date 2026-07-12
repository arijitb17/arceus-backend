package com.arijit.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "phone_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    // Nullable: required for LOCAL signups (enforced in RegisterRequest
    // validation, not here), but Google/GitHub users never provide one.
    @Column(name = "phone_number", unique = true)
    private String phoneNumber;

    // Nullable on purpose: a user who signed up via Google/GitHub has no
    // local password at all - there's nothing to hash and store.
    private String password;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    private String providerId;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = new HashSet<>(Set.of(Role.ROLE_USER));

    // Tracked separately, not as one "verified" flag, since email and phone
    // are confirmed independently (different OTPs, different channels).
    @Builder.Default
    private boolean emailVerified = false;

    @Builder.Default
    private boolean phoneVerified = false;

    // Distinct from isFullyVerified(): that's the user's own doing (OTP
    // confirmation); this is an admin action (suspend an account without
    // touching its verification state, so re-enabling doesn't require
    // the user to re-verify anything).
    @Column(name = "account_enabled", nullable = false)
    @Builder.Default
    private boolean accountEnabled = true;

    // The account can only log in once BOTH are true. This is what
    // CustomUserDetailsService reads to decide if Spring Security should
    // reject a login attempt with "account disabled".
    //
    // Google/GitHub users skip this entirely - the provider already vouched
    // for their identity (and they never gave us a phone number to verify).
    public boolean isFullyVerified() {
        if (provider != AuthProvider.LOCAL) {
            return true;
        }
        return emailVerified && phoneVerified;
    }

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
