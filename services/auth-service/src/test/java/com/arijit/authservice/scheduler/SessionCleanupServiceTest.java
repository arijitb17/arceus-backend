package com.arijit.authservice.scheduler;

import com.arijit.authservice.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock RefreshTokenRepository repository;
    @InjectMocks SessionCleanupService service;

    @Test
    void deletesTokensExpiredBeforeNow() {
        when(repository.deleteByExpiryDateBefore(org.mockito.ArgumentMatchers.any())).thenReturn(3);
        Instant before = Instant.now();

        service.cleanExpiredSessions();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteByExpiryDateBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));
    }

    @Test
    void handlesNothingToDelete() {
        when(repository.deleteByExpiryDateBefore(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        assertThatCode(service::cleanExpiredSessions).doesNotThrowAnyException();
    }
}
