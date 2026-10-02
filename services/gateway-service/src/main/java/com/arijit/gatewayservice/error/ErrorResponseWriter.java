package com.arijit.gatewayservice.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Filters run before the DispatcherServlet, so GlobalExceptionHandler never
 * sees anything they reject outright (an expired JWT, a rate-limited
 * request). This writes the exact same ApiError shape by hand so the
 * client sees one consistent error format regardless of where in the
 * pipeline the rejection happened.
 * <p>
 * WRITE_DATES_AS_TIMESTAMPS is disabled explicitly - a bare
 * `new ObjectMapper()` defaults it to true, which serializes ApiError's
 * Instant `timestamp` field as a raw epoch-seconds number (e.g.
 * "1786576690.776551100") instead of an ISO-8601 string. See
 * auth-service's JacksonConfig for the fuller explanation of this same
 * mistake elsewhere in the platform.
 */
@Component
public class ErrorResponseWriter {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String error, String message) throws IOException {
        write(request, response, status, error, message, Map.of());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String error,
                       String message, Map<String, String> extraHeaders) throws IOException {
        String correlationId = String.valueOf(request.getAttribute("requestId"));
        extraHeaders.forEach(response::setHeader);
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiError.of(status, error, message, correlationId)));
    }
}

