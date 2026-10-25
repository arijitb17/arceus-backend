package com.arijit.gatewayservice.proxy;

import com.arijit.arceus.grpc.gateway.HttpHeader;
import com.arijit.arceus.grpc.gateway.HttpRequestMessage;
import com.arijit.arceus.grpc.gateway.HttpResponseMessage;
import com.google.protobuf.ByteString;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HttpMessageMapperTest {

    private final HttpMessageMapper mapper = new HttpMessageMapper();

    private MockHttpServletRequest request() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/cache/ns/k");
        r.setQueryString("a=1&b=2");
        r.setContent("{\"x\":1}".getBytes(StandardCharsets.UTF_8));
        r.addHeader("Content-Type", "application/json");
        r.addHeader("Content-Length", "7");
        r.addHeader("Host", "gateway");
        r.addHeader("Connection", "keep-alive");
        r.addHeader("X-Custom", "v1");
        r.addHeader("X-Custom", "v2");
        return r;
    }

    private List<String> headerValues(HttpRequestMessage m, String name) {
        return m.getHeadersList().stream().filter(h -> h.getName().equalsIgnoreCase(name))
                .map(HttpHeader::getValue).collect(Collectors.toList());
    }

    @Test
    void mapsCoreFieldsAndBody() throws Exception {
        HttpRequestMessage m = mapper.toRequestMessage(request(), "/cache/ns/k", "rid", null, null);

        assertThat(m.getRequestId()).isEqualTo("rid");
        assertThat(m.getMethod()).isEqualTo("POST");
        assertThat(m.getPath()).isEqualTo("/cache/ns/k");
        assertThat(m.getQueryString()).isEqualTo("a=1&b=2");
        assertThat(m.getBody().toStringUtf8()).isEqualTo("{\"x\":1}");
    }

    @Test
    void nullQueryAndRequestIdBecomeEmpty() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/x");

        HttpRequestMessage m = mapper.toRequestMessage(r, "/x", null, null, null);

        assertThat(m.getQueryString()).isEmpty();
        assertThat(m.getRequestId()).isEmpty();
        assertThat(m.getBody()).isEmpty();
    }

    @Test
    void dropsHopByHopHeadersButKeepsRepeatedOnes() throws Exception {
        HttpRequestMessage m = mapper.toRequestMessage(request(), "/p", "rid", null, null);

        assertThat(headerValues(m, "content-length")).isEmpty();
        assertThat(headerValues(m, "host")).isEmpty();
        assertThat(headerValues(m, "connection")).isEmpty();
        assertThat(headerValues(m, "content-type")).containsExactly("application/json");
        assertThat(headerValues(m, "x-custom")).containsExactly("v1", "v2");
    }

    @Test
    void stripsClientSuppliedTrustHeadersThenAddsVerifiedOnes() throws Exception {
        MockHttpServletRequest r = request();
        r.addHeader("X-User-Email", "attacker@evil.com");
        r.addHeader("X-User-Roles", "ROLE_ADMIN");
        r.addHeader("X-Tenant-Project-Id", "forged");
        r.addHeader("X-Api-Key-Scopes", "everything");

        HttpRequestMessage m = mapper.toRequestMessage(r, "/p", "rid", "real@user.com", List.of("ROLE_USER"));

        assertThat(headerValues(m, "x-user-email")).containsExactly("real@user.com");
        assertThat(headerValues(m, "x-user-roles")).containsExactly("ROLE_USER");
        assertThat(headerValues(m, "x-tenant-project-id")).isEmpty();
        assertThat(headerValues(m, "x-api-key-scopes")).isEmpty();
        assertThat(m.getUserEmail()).isEqualTo("real@user.com");
    }

    @Test
    void anonymousRequestGetsNoIdentityHeaders() throws Exception {
        MockHttpServletRequest r = request();
        r.addHeader("X-User-Email", "attacker@evil.com");

        HttpRequestMessage m = mapper.toRequestMessage(r, "/p", "rid", "  ", List.of());

        assertThat(headerValues(m, "x-user-email")).isEmpty();
        assertThat(headerValues(m, "x-user-roles")).isEmpty();
        assertThat(m.getUserEmail()).isEmpty();
    }

    @Test
    void joinsMultipleRolesWithComma() throws Exception {
        HttpRequestMessage m = mapper.toRequestMessage(request(), "/p", "rid", "a@b.c", List.of("ROLE_USER", "ROLE_ADMIN"));

        assertThat(headerValues(m, "x-user-roles")).containsExactly("ROLE_USER,ROLE_ADMIN");
    }

    @Test
    void tenantHeadersOnlyForPresentValues() throws Exception {
        var tenant = new HttpMessageMapper.TenantHeaders("org", "proj", "prod", "SECRET", " ");

        HttpRequestMessage m = mapper.toRequestMessage(request(), "/p", "rid", "a@b.c", List.of("ROLE_USER"), tenant);

        assertThat(headerValues(m, "x-tenant-organization-id")).containsExactly("org");
        assertThat(headerValues(m, "x-tenant-project-id")).containsExactly("proj");
        assertThat(headerValues(m, "x-tenant-environment")).containsExactly("prod");
        assertThat(headerValues(m, "x-api-key-type")).containsExactly("SECRET");
        assertThat(headerValues(m, "x-api-key-scopes")).isEmpty();
    }

    @Test
    void writeResponseCopiesStatusHeadersAndBody() throws Exception {
        HttpResponseMessage msg = HttpResponseMessage.newBuilder().setStatus(201)
                .addHeaders(HttpHeader.newBuilder().setName("Content-Type").setValue("text/plain"))
                .addHeaders(HttpHeader.newBuilder().setName("Content-Length").setValue("999"))
                .addHeaders(HttpHeader.newBuilder().setName("Transfer-Encoding").setValue("chunked"))
                .setBody(ByteString.copyFromUtf8("hello")).build();
        MockHttpServletResponse response = new MockHttpServletResponse();

        mapper.writeResponse(msg, response);

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("Content-Type")).isEqualTo("text/plain");
        assertThat(response.getHeader("Transfer-Encoding")).isNull();
        assertThat(response.getContentAsString()).isEqualTo("hello");
    }

    @Test
    void writeResponseWithEmptyBodyWritesNothing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        mapper.writeResponse(HttpResponseMessage.newBuilder().setStatus(204).build(), response);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void bodyAsUtf8() {
        assertThat(HttpMessageMapper.bodyAsUtf8(HttpResponseMessage.newBuilder().setBody(ByteString.copyFromUtf8("héllo")).build()))
                .isEqualTo("héllo");
    }
}
