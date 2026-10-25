package com.arijit.organizationservice.grpc;

import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.organization.*;
import com.arijit.organizationservice.entity.*;
import com.arijit.organizationservice.messaging.GithubConnectionSyncConsumer;
import com.arijit.organizationservice.repository.GithubConnectionRepository;
import com.arijit.organizationservice.repository.ProjectEnvironmentRepository;
import com.arijit.organizationservice.repository.ProjectRepository;
import com.arijit.organizationservice.service.OrgAccessGuard;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class OrganizationGrpcAndSyncTest {

    @Mock OrgAccessGuard guard;
    @Mock ProjectRepository projectRepo;
    @Mock ProjectEnvironmentRepository envRepo;
    @Mock GithubConnectionRepository githubRepo;

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private <T> T captured(StreamObserver<T> obs, Class<T> type) {
        ArgumentCaptor<T> cap = ArgumentCaptor.forClass(type);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        return cap.getValue();
    }

    @Nested
    class Access {

        private final OrganizationAccessGrpcService grpc() {
            return new OrganizationAccessGrpcService(guard, projectRepo, envRepo);
        }

        private CheckManagerAccessRequest request(String org, String email) {
            return CheckManagerAccessRequest.newBuilder().setOrganizationId(org).setUserEmail(email).build();
        }

        @Test
        void managerAccessForOwnerAndAdminOnly() {
            when(guard.roleOfIfMember(orgId, "owner")).thenReturn(Optional.of(OrgRole.OWNER));
            when(guard.roleOfIfMember(orgId, "admin")).thenReturn(Optional.of(OrgRole.ADMIN));
            when(guard.roleOfIfMember(orgId, "member")).thenReturn(Optional.of(OrgRole.MEMBER));
            when(guard.roleOfIfMember(orgId, "none")).thenReturn(Optional.empty());

            for (Object[] c : new Object[][]{{"owner", true, "OWNER"}, {"admin", true, "ADMIN"}, {"member", false, "MEMBER"}, {"none", false, ""}}) {
                StreamObserver<CheckManagerAccessResponse> obs = mock(StreamObserver.class);
                grpc().checkManagerAccess(request(orgId.toString(), (String) c[0]), obs);
                CheckManagerAccessResponse r = captured(obs, CheckManagerAccessResponse.class);
                assertThat(r.getAuthorized()).isEqualTo(c[1]);
                assertThat(r.getRole()).isEqualTo(c[2]);
            }
        }

        @Test
        void malformedOrgIdIsUnauthorizedWithoutLookup() {
            StreamObserver<CheckManagerAccessResponse> obs = mock(StreamObserver.class);

            grpc().checkManagerAccess(request("junk", "a"), obs);

            assertThat(captured(obs, CheckManagerAccessResponse.class).getAuthorized()).isFalse();
            verifyNoInteractions(guard);
        }

        @Test
        void memberAccessAcceptsAnyRole() {
            when(guard.roleOfIfMember(orgId, "m")).thenReturn(Optional.of(OrgRole.MEMBER));
            StreamObserver<CheckMemberAccessResponse> obs = mock(StreamObserver.class);

            grpc().checkMemberAccess(request(orgId.toString(), "m"), obs);

            CheckMemberAccessResponse r = captured(obs, CheckMemberAccessResponse.class);
            assertThat(r.getAuthorized()).isTrue();
            assertThat(r.getRole()).isEqualTo("MEMBER");
        }

        @Test
        void projectExistsChecksTenantScopedRepositoryMethod() {
            when(projectRepo.existsByIdAndOrganizationIdAndDeletedAtIsNull(projectId, orgId)).thenReturn(true);
            StreamObserver<CheckProjectExistsResponse> yes = mock(StreamObserver.class);
            StreamObserver<CheckProjectExistsResponse> bad = mock(StreamObserver.class);

            grpc().checkProjectExists(CheckProjectExistsRequest.newBuilder().setOrganizationId(orgId.toString()).setProjectId(projectId.toString()).build(), yes);
            grpc().checkProjectExists(CheckProjectExistsRequest.newBuilder().setOrganizationId("x").setProjectId(projectId.toString()).build(), bad);

            assertThat(captured(yes, CheckProjectExistsResponse.class).getExists()).isTrue();
            assertThat(captured(bad, CheckProjectExistsResponse.class).getExists()).isFalse();
        }

        @Test
        void trackedBranchesMapNullBranchToEmpty() {
            when(envRepo.findByProjectId(projectId)).thenReturn(List.of(
                    ProjectEnvironment.builder().environment(EnvironmentType.DEVELOPMENT).trackedBranch(null).build(),
                    ProjectEnvironment.builder().environment(EnvironmentType.PRODUCTION).trackedBranch("main").build()));
            StreamObserver<GetTrackedBranchesResponse> obs = mock(StreamObserver.class);

            grpc().getTrackedBranches(GetTrackedBranchesRequest.newBuilder().setProjectId(projectId.toString()).build(), obs);

            GetTrackedBranchesResponse r = captured(obs, GetTrackedBranchesResponse.class);
            assertThat(r.getBranchesList()).extracting(TrackedBranch::getEnvironment, TrackedBranch::getBranch)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("DEVELOPMENT", ""), org.assertj.core.groups.Tuple.tuple("PRODUCTION", "main"));
        }

        @Test
        void trackedBranchesForMalformedProjectIsEmpty() {
            StreamObserver<GetTrackedBranchesResponse> obs = mock(StreamObserver.class);

            grpc().getTrackedBranches(GetTrackedBranchesRequest.newBuilder().setProjectId("nope").build(), obs);

            assertThat(captured(obs, GetTrackedBranchesResponse.class).getBranchesList()).isEmpty();
            verifyNoInteractions(envRepo);
        }

        @Test
        void ping() {
            StreamObserver<PingResponse> obs = mock(StreamObserver.class);

            grpc().ping(PingRequest.getDefaultInstance(), obs);

            assertThat(captured(obs, PingResponse.class).getService()).isEqualTo("organization-service");
        }
    }

    @Nested
    class GithubSync {

        private EventEnvelope event(String type, Map<String, Object> payload, String user) {
            return EventEnvelope.of(type, Instant.now(), "deployment-service", null, user, null, null, payload);
        }

        private Map<String, Object> connectPayload() {
            return Map.of("projectId", projectId.toString(), "repoFullName", "acme/web", "defaultBranch", "main",
                    "installationId", UUID.randomUUID().toString());
        }

        @Test
        void connectedEventReplacesExistingConnection() {
            GithubConnection old = GithubConnection.builder().projectId(projectId).repoFullName("acme/old").build();
            when(githubRepo.findByProjectIdAndDisconnectedAtIsNull(projectId)).thenReturn(Optional.of(old));

            new GithubConnectionSyncConsumer(githubRepo).onGithubEvent(event("GITHUB_REPO_CONNECTED", connectPayload(), "alice"));

            assertThat(old.getDisconnectedAt()).isNotNull();
            ArgumentCaptor<GithubConnection> cap = ArgumentCaptor.forClass(GithubConnection.class);
            verify(githubRepo, times(2)).save(cap.capture());
            GithubConnection created = cap.getAllValues().get(1);
            assertThat(created.getRepoFullName()).isEqualTo("acme/web");
            assertThat(created.getDefaultBranch()).isEqualTo("main");
            assertThat(created.getConnectedBy()).isEqualTo("alice");
        }

        @Test
        void connectedEventWithoutUserUsesEmptyActor() {
            when(githubRepo.findByProjectIdAndDisconnectedAtIsNull(any())).thenReturn(Optional.empty());

            new GithubConnectionSyncConsumer(githubRepo).onGithubEvent(event("GITHUB_REPO_CONNECTED", connectPayload(), null));

            ArgumentCaptor<GithubConnection> cap = ArgumentCaptor.forClass(GithubConnection.class);
            verify(githubRepo).save(cap.capture());
            assertThat(cap.getValue().getConnectedBy()).isEmpty();
        }

        @Test
        void disconnectedEventMarksActiveConnection() {
            GithubConnection active = GithubConnection.builder().projectId(projectId).build();
            when(githubRepo.findByProjectIdAndDisconnectedAtIsNull(projectId)).thenReturn(Optional.of(active));

            new GithubConnectionSyncConsumer(githubRepo).onGithubEvent(
                    event("GITHUB_REPO_DISCONNECTED", Map.of("projectId", projectId.toString()), null));

            assertThat(active.getDisconnectedAt()).isNotNull();
            verify(githubRepo).save(active);
        }

        @Test
        void disconnectWithNothingActiveIsNoOp() {
            when(githubRepo.findByProjectIdAndDisconnectedAtIsNull(any())).thenReturn(Optional.empty());

            new GithubConnectionSyncConsumer(githubRepo).onGithubEvent(
                    event("GITHUB_REPO_DISCONNECTED", Map.of("projectId", projectId.toString()), null));

            verify(githubRepo, never()).save(any());
        }

        @Test
        void malformedEventsAreDropped() {
            GithubConnectionSyncConsumer consumer = new GithubConnectionSyncConsumer(githubRepo);

            consumer.onGithubEvent(event("GITHUB_REPO_CONNECTED", Map.of(), null));
            consumer.onGithubEvent(event("GITHUB_REPO_CONNECTED", Map.of("projectId", "not-a-uuid"), null));
            consumer.onGithubEvent(event("GITHUB_REPO_CONNECTED", Map.of("projectId", projectId.toString(), "repoFullName", "a/b"), null));
            consumer.onGithubEvent(event("SOMETHING_ELSE", Map.of("projectId", projectId.toString()), null));

            verifyNoInteractions(githubRepo);
        }
    }
}
