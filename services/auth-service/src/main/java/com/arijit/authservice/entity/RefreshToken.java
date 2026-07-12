package com.arijit.authservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// A refresh token lets a client get a new access token without the user
// logging in again. We store its hash server-side (unlike the access
// token) so we can revoke it on logout and check it hasn't already been
// used/expired - and so that a database read alone can never hand back a
// usable bearer credential, the same reasoning as never storing a
// plaintext password. The raw opaque token (see AuthService.generateOpaqueToken)
// is deliberately NOT a JWT - a refresh token has no reason to be
// self-describing or verifiable offline; it exists purely as an opaque
// lookup key into this table.
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Instant expiryDate;

    // Nullable for rows created before this column existed (ddl-auto: update
    // doesn't backfill) - SessionResponse falls back to null cleanly rather
    // than throwing for old rows.
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired() {
        return expiryDate.isBefore(Instant.now());
    }
}
