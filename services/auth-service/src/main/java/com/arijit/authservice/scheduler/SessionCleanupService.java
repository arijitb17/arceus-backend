package com.arijit.authservice.scheduler;

import com.arijit.authservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
