package com.arijit.ratelimitservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionMapped() {
        var r = handler.handleApiException(new ApiException("bad", HttpStatus.BAD_REQUEST));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody()).containsEntry("message", "bad").containsEntry("status", 400);
    }

    @Test
    void genericIsOpaque() {
        var r = handler.handleGeneric(new RuntimeException("secret"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).containsEntry("message", "Something went wrong");
    }
}
