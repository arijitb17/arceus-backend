package com.arijit.authservice.grpc;

import com.arijit.arceus.grpc.apikey.*;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.ApiKeyType;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.entity.User;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.service.ApiKeyService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyProvisioningGrpcServiceTest {

    @Mock ApiKeyService apiKeyService;
    @Mock StreamObserver<GetOrCreateProjectPublicKeyResponse> keyObserver;
    @Mock StreamObserver<ValidateApiKeyResponse> validateObserver;
    @Mock StreamObserver<PingResponse> pingObserver;
    @InjectMocks ApiKeyProvisioningGrpcService grpc;

    private final UUID org = UUID.randomUUID();
    private final UUID project = UUID.randomUUID();

    private GetOrCreateProjectPublicKeyRequest keyRequest(String orgId, String projectId) {
        return GetOrCreateProjectPublicKeyRequest.newBuilder()
                .setOrganizationId(orgId).setProjectId(projectId)
                .setEnvironment("prod").setRequestedBy("a@b.com").build();
    }

    @Test
    void getOrCreateReturnsServiceResult() {
        when(apiKeyService.getOrCreateProjectPublicKey("a@b.com", org, project, "prod"))
                .thenReturn(new ApiKeyService.ProjectPublicKeyResult(true, "pk_raw", "pk_raw00"));

        grpc.getOrCreateProjectPublicKey(keyRequest(org.toString(), project.toString()), keyObserver);

        ArgumentCaptor<GetOrCreateProjectPublicKeyResponse> cap = ArgumentCaptor.forClass(GetOrCreateProjectPublicKeyResponse.class);
        verify(keyObserver).onNext(cap.capture());
        verify(keyObserver).onCompleted();
        assertThat(cap.getValue().getCreated()).isTrue();
        assertThat(cap.getValue().getRawKey()).isEqualTo("pk_raw");
        assertThat(cap.getValue().getKeyPrefix()).isEqualTo("pk_raw00");
    }

    @Test
    void malformedUuidReturnsErrorResponseNotException() {
        grpc.getOrCreateProjectPublicKey(keyRequest("not-a-uuid", project.toString()), keyObserver);

        ArgumentCaptor<GetOrCreateProjectPublicKeyResponse> cap = ArgumentCaptor.forClass(GetOrCreateProjectPublicKeyResponse.class);
        verify(keyObserver).onNext(cap.capture());
        verify(keyObserver).onCompleted();
        verify(keyObserver, never()).onError(any());
        assertThat(cap.getValue().getError()).isEqualTo("Malformed request");
        assertThat(cap.getValue().getCreated()).isFalse();
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void apiExceptionBecomesErrorField() {
        when(apiKeyService.getOrCreateProjectPublicKey(any(), any(), any(), any()))
                .thenThrow(new ApiException("Requires OWNER or ADMIN role in this organization", HttpStatus.FORBIDDEN));

        grpc.getOrCreateProjectPublicKey(keyRequest(org.toString(), project.toString()), keyObserver);

        ArgumentCaptor<GetOrCreateProjectPublicKeyResponse> cap = ArgumentCaptor.forClass(GetOrCreateProjectPublicKeyResponse.class);
        verify(keyObserver).onNext(cap.capture());
        assertThat(cap.getValue().getError()).contains("OWNER or ADMIN");
        assertThat(cap.getValue().getRawKey()).isEmpty();
    }

    @Test
    void validateUnknownKeyIsInvalid() {
        when(apiKeyService.validateAndTouch("bad")).thenReturn(Optional.empty());

        grpc.validateApiKey(ValidateApiKeyRequest.newBuilder().setRawKey("bad").build(), validateObserver);

        ArgumentCaptor<ValidateApiKeyResponse> cap = ArgumentCaptor.forClass(ValidateApiKeyResponse.class);
        verify(validateObserver).onNext(cap.capture());
        verify(validateObserver).onCompleted();
        assertThat(cap.getValue().getValid()).isFalse();
    }

    @Test
    void validatePersonalKeyReturnsEmailAndRolesOnly() {
        User u = User.builder().email("a@b.com").roles(Set.of(Role.ROLE_USER)).build();
        when(apiKeyService.validateAndTouch("ak_ok")).thenReturn(Optional.of(ApiKey.builder().user(u).build()));

        grpc.validateApiKey(ValidateApiKeyRequest.newBuilder().setRawKey("ak_ok").build(), validateObserver);

        ArgumentCaptor<ValidateApiKeyResponse> cap = ArgumentCaptor.forClass(ValidateApiKeyResponse.class);
        verify(validateObserver).onNext(cap.capture());
        assertThat(cap.getValue().getValid()).isTrue();
        assertThat(cap.getValue().getUserEmail()).isEqualTo("a@b.com");
        assertThat(cap.getValue().getRoles()).isEqualTo("ROLE_USER");
        assertThat(cap.getValue().getProjectId()).isEmpty();
    }

    @Test
    void validateProjectKeyIncludesTenantContext() {
        User u = User.builder().email("a@b.com").roles(Set.of(Role.ROLE_USER)).build();
        ApiKey key = ApiKey.builder().user(u).organizationId(org).projectId(project)
                .environment("prod").keyType(ApiKeyType.SECRET).scopes(null).build();
        when(apiKeyService.validateAndTouch("sk_ok")).thenReturn(Optional.of(key));

        grpc.validateApiKey(ValidateApiKeyRequest.newBuilder().setRawKey("sk_ok").build(), validateObserver);

        ArgumentCaptor<ValidateApiKeyResponse> cap = ArgumentCaptor.forClass(ValidateApiKeyResponse.class);
        verify(validateObserver).onNext(cap.capture());
        ValidateApiKeyResponse r = cap.getValue();
        assertThat(r.getOrganizationId()).isEqualTo(org.toString());
        assertThat(r.getProjectId()).isEqualTo(project.toString());
        assertThat(r.getEnvironment()).isEqualTo("prod");
        assertThat(r.getKeyType()).isEqualTo("SECRET");
        assertThat(r.getScopes()).isEmpty();
    }

    @Test
    void pingReportsService() {
        grpc.ping(PingRequest.getDefaultInstance(), pingObserver);

        ArgumentCaptor<PingResponse> cap = ArgumentCaptor.forClass(PingResponse.class);
        verify(pingObserver).onNext(cap.capture());
        verify(pingObserver).onCompleted();
        assertThat(cap.getValue().getOk()).isTrue();
        assertThat(cap.getValue().getService()).isEqualTo("auth-service");
    }
}
