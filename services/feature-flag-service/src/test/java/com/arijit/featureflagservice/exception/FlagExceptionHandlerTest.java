package com.arijit.featureflagservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class FlagExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiException() {
        var r = handler.handleApiException(new ApiException("bad", HttpStatus.BAD_REQUEST));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("message", "bad");
    }

    @Test
    void optimisticLockIsConflict() {
        var r = handler.handleOptimisticLock(new OptimisticLockingFailureException("stale"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void dataIntegrityIsConflict() {
        var r = handler.handleDataIntegrity(new DataIntegrityViolationException("dup key value violates"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().get("message").toString()).doesNotContain("violates");
    }

    @Test
    void genericIsOpaque() {
        var r = handler.handleGeneric(new RuntimeException("secret"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().get("message").toString()).doesNotContain("secret");
    }
}
