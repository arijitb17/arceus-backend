package com.arijit.authservice.scheduler;

import com.arijit.authservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The "clean expired sessions" scheduled job - deletes every RefreshToken
 * row whose expiryDate has passed. Without this, expired refresh tokens
 * (already unusable - {@link com.arijit.authservice.entity.RefreshToken#isExpired()}
 * rejects them on every lookup) just accumulate in the table forever.
 * <p>
 * Invoked by scheduler-service's HTTP_CALLBACK executor via
 * SessionCleanupCallbackController, on a recurring job
 * SessionCleanupJobRegistrar registers once at startup - same delegation
 * notification-service uses for its own retry sweep (see
 * NotificationRetryScheduler's Javadoc for the platform-wide reasoning).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void cleanExpiredSessions() {
        int removed = refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
        if (removed > 0) {
            log.info("Session cleanup: removed {} expired refresh token(s)", removed);
        } else {
            log.debug("Session cleanup: no expired refresh tokens to remove");
        }
    }
}
