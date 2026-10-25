package com.arijit.cacheservice.exception;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void apiExceptionKeepsStatusAndMessage() {
        var r = handler.handleApiException(new ApiException("too big", HttpStatus.PAYLOAD_TOO_LARGE));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(r.getBody()).containsEntry("message", "too big").containsEntry("status", 413);
    }

    @Test
    void serverErrorApiExceptionAlsoHandled() {
        var r = handler.handleApiException(new ApiException("boom", HttpStatus.INTERNAL_SERVER_ERROR));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void validationErrorsReturnFieldMap() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "field", "field is required"));

        var r = handler.handleValidation(new MethodArgumentNotValidException(
                new MethodParameter(Object.class.getDeclaredMethod("toString"), -1), binding));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("fields")).isEqualTo(Map.of("field", "field is required"));
    }

    @Test
    void genericHidesInternalMessage() {
        var r = handler.handleGeneric(new IllegalStateException("redis password"));

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody()).containsEntry("message", "Something went wrong");
    }

    @Test
    void includesCorrelationId() {
        MDC.put("correlationId", "c-1");

        assertThat(handler.handleGeneric(new RuntimeException()).getBody()).containsEntry("correlationId", "c-1");
    }
}
