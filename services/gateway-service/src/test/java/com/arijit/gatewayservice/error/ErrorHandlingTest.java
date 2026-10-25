package com.arijit.gatewayservice.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorHandlingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ErrorResponseWriter writer = new ErrorResponseWriter();
    private final ObjectMapper mapper = new ObjectMapper();

    private MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/api/nothing");
        r.setRequestURI("/api/nothing");
        r.setAttribute("requestId", "rid-1");
        return r;
    }

    @Test
    void apiExceptionKeepsStatusAndCorrelationId() {
        var r = handler.handleApiException(new ApiException("upstream down", HttpStatus.BAD_GATEWAY), request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(r.getBody().message()).isEqualTo("upstream down");
        assertThat(r.getBody().correlationId()).isEqualTo("rid-1");
        assertThat(r.getBody().status()).isEqualTo(502);
    }

    @Test
    void unknownRouteIs404WithPath() {
        var r = handler.handleNotFound(new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "/api/nothing"), request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().message()).isEqualTo("No route matches /api/nothing");
    }

    @Test
    void methodNotAllowed() {
        var r = handler.handleMethodNotAllowed(new HttpRequestMethodNotSupportedException("TRACE"), request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void genericDoesNotLeakDetails() {
        var r = handler.handleGeneric(new RuntimeException("db password"), request());

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().message()).doesNotContain("db password");
    }

    @Test
    void apiErrorFactoriesAndNullFieldsAreOmittedInJson() throws Exception {
        ApiError plain = ApiError.of(400, "Bad Request", "m", "c");
        ApiError withFields = ApiError.of(400, "Bad Request", "m", "c", Map.of("f", "bad"));

        JsonNode plainJson = mapper.findAndRegisterModules().valueToTree(plain);
        JsonNode fieldsJson = mapper.findAndRegisterModules().valueToTree(withFields);

        assertThat(plainJson.has("fields")).isFalse();
        assertThat(fieldsJson.get("fields").get("f").asText()).isEqualTo("bad");
        assertThat(plain.timestamp()).isNotNull();
    }

    @Test
    void writerProducesJsonBodyAndExtraHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request(), response, 429, "Too Many Requests", "slow down", Map.of("Retry-After", "5"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("message").asText()).isEqualTo("slow down");
        assertThat(body.get("correlationId").asText()).isEqualTo("rid-1");
        assertThat(body.get("timestamp").isTextual()).isTrue();
    }

    @Test
    void writerWithoutExtraHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(request(), response, 401, "Unauthorized", "no");

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
