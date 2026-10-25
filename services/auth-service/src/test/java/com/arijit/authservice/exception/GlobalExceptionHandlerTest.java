package com.arijit.authservice.exception;

import com.arijit.arceus.events.domain.CorrelationConstants;
import com.arijit.arceus.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
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

    private void assertBody(ResponseEntity<Map<String, Object>> r, HttpStatus status, String message) {
        assertThat(r.getStatusCode()).isEqualTo(status);
        assertThat(r.getBody()).containsEntry("status", status.value())
                .containsEntry("error", status.getReasonPhrase())
                .containsEntry("message", message)
                .containsKey("timestamp");
    }

    @Test
    void apiExceptionUsesItsStatusAndMessage() {
        assertBody(handler.handleApiException(new ApiException("nope", HttpStatus.CONFLICT)),
                HttpStatus.CONFLICT, "nope");
    }

    @Test
    void rateLimitAddsRetryAfterHeader() {
        var r = handler.handleRateLimitExceeded(new RateLimitExceededException("slow", 12));

        assertBody(r, HttpStatus.TOO_MANY_REQUESTS, "slow");
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("12");
    }

    @Test
    void badCredentialsHidesDetail() {
        assertBody(handler.handleBadCredentials(new BadCredentialsException("secret detail")),
                HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    @Test
    void disabledLockedAndAccessDenied() {
        assertThat(handler.handleDisabled(new DisabledException("x")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleDisabled(new DisabledException("x")).getBody().get("message").toString())
                .contains("not fully verified");
        assertThat(handler.handleLocked(new LockedException("x")).getBody().get("message").toString())
                .contains("disabled by an administrator");
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().get("message"))
                .isEqualTo("You don't have permission to do that");
    }

    @Test
    void validationErrorsListFieldMessages() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "req");
        binding.addError(new FieldError("req", "email", "Email must be valid"));
        binding.addError(new FieldError("req", "name", "Name is required"));
        MethodParameter param = new MethodParameter(Object.class.getDeclaredMethod("toString"), -1);

        var r = handler.handleValidation(new MethodArgumentNotValidException(param, binding));

        assertBody(r, HttpStatus.BAD_REQUEST, "Validation failed");
        assertThat((Map<String, String>) r.getBody().get("fields"))
                .containsEntry("email", "Email must be valid")
                .containsEntry("name", "Name is required");
    }

    @Test
    void genericExceptionDoesNotLeakMessage() {
        var r = handler.handleGeneric(new RuntimeException("db password is hunter2"));

        assertBody(r, HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");
    }

    @Test
    void includesCorrelationIdFromMdc() {
        MDC.put(CorrelationConstants.MDC_KEY, "corr-42");

        assertThat(handler.handleApiException(new ApiException("x", HttpStatus.BAD_REQUEST)).getBody())
                .containsEntry("correlationId", "corr-42");
    }
}
