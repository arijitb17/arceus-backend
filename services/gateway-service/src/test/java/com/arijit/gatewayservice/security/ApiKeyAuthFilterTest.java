package com.arijit.gatewayservice.security;

import com.arijit.arceus.grpc.apikey.ApiKeyProvisioningGrpcServiceGrpc;
import com.arijit.arceus.grpc.apikey.ValidateApiKeyRequest;
import com.arijit.arceus.grpc.apikey.ValidateApiKeyResponse;
import com.arijit.gatewayservice.error.ErrorResponseWriter;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock ApiKeyProvisioningGrpcServiceGrpc.ApiKeyProvisioningGrpcServiceBlockingStub stub;
    @Mock FilterChain chain;

    private ApiKeyAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthFilter(stub, new ErrorResponseWriter());
        request = new MockHttpServletRequest("GET", "/api/cache/x");
        response = new MockHttpServletResponse();
    }

    @Test
    void noHeaderFallsThroughToJwtFilter() throws Exception {
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(stub);
    }

    @Test
    void blankHeaderTreatedAsAbsent() throws Exception {
        request.addHeader("X-API-Key", "  ");

        filter.doFilter(request, response, chain);

        verifyNoInteractions(stub);
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidKeyIs401AndStopsChain() throws Exception {
        request.addHeader("X-API-Key", "ak_bad");
        when(stub.validateApiKey(any())).thenReturn(ValidateApiKeyResponse.newBuilder().setValid(false).build());

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or revoked API key");
        verifyNoInteractions(chain);
    }

    @Test
    void authServiceOutageIs503() throws Exception {
        request.addHeader("X-API-Key", "ak_x");
        when(stub.validateApiKey(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(chain);
    }

    @Test
    void personalKeySetsUserAttributesOnly() throws Exception {
        request.addHeader("X-API-Key", "ak_ok");
        when(stub.validateApiKey(any())).thenReturn(ValidateApiKeyResponse.newBuilder()
                .setValid(true).setUserEmail("a@b.com").setRoles("ROLE_USER, ROLE_ADMIN ,").build());

        filter.doFilter(request, response, chain);

        ArgumentCaptor<ValidateApiKeyRequest> cap = ArgumentCaptor.forClass(ValidateApiKeyRequest.class);
        verify(stub).validateApiKey(cap.capture());
        assertThat(cap.getValue().getRawKey()).isEqualTo("ak_ok");
        assertThat(request.getAttribute(JwtAuthFilter.USER_EMAIL_ATTRIBUTE)).isEqualTo("a@b.com");
        assertThat(request.getAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE)).isEqualTo(List.of("ROLE_USER", "ROLE_ADMIN"));
        assertThat(request.getAttribute(ApiKeyAuthFilter.TENANT_PROJECT_ID_ATTRIBUTE)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void projectKeySetsTenantAttributes() throws Exception {
        request.addHeader("X-API-Key", "sk_ok");
        when(stub.validateApiKey(any())).thenReturn(ValidateApiKeyResponse.newBuilder()
                .setValid(true).setUserEmail("a@b.com").setRoles("ROLE_USER")
                .setOrganizationId("org").setProjectId("proj").setEnvironment("prod")
                .setKeyType("SECRET").setScopes("read").build());

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(ApiKeyAuthFilter.TENANT_ORGANIZATION_ID_ATTRIBUTE)).isEqualTo("org");
        assertThat(request.getAttribute(ApiKeyAuthFilter.TENANT_PROJECT_ID_ATTRIBUTE)).isEqualTo("proj");
        assertThat(request.getAttribute(ApiKeyAuthFilter.TENANT_ENVIRONMENT_ATTRIBUTE)).isEqualTo("prod");
        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_TYPE_ATTRIBUTE)).isEqualTo("SECRET");
        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_SCOPES_ATTRIBUTE)).isEqualTo("read");
    }

    @Test
    void emptyScopesNotExposed() throws Exception {
        request.addHeader("X-API-Key", "pk_ok");
        when(stub.validateApiKey(any())).thenReturn(ValidateApiKeyResponse.newBuilder()
                .setValid(true).setUserEmail("a@b.com").setRoles("").setProjectId("p").setOrganizationId("o").build());

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(ApiKeyAuthFilter.API_KEY_SCOPES_ATTRIBUTE)).isNull();
        assertThat(request.getAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE)).isEqualTo(List.of());
    }
}
