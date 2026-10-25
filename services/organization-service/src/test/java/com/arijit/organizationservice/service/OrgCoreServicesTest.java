package com.arijit.organizationservice.service;

import com.arijit.organizationservice.dto.*;
import com.arijit.organizationservice.entity.*;
import com.arijit.organizationservice.exception.ApiException;
import com.arijit.organizationservice.messaging.OrganizationEventPublisher;
import com.arijit.organizationservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrgCoreServicesTest {

    @Mock OrganizationRepository orgRepo;
    @Mock OrganizationMemberRepository memberRepo;
    @Mock ProjectRepository projectRepo;
    @Mock ProjectEnvironmentRepository envRepo;
    @Mock SecretRepository secretRepo;
    @Mock OrganizationEventPublisher publisher;

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    private void assertApi(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    private OrganizationMember member(String user, OrgRole role) {
        return OrganizationMember.builder().id(UUID.randomUUID()).organizationId(orgId).userId(user).role(role).build();
    }

    // ------------------------------------------------------------------ OrgAccessGuard

    @Nested
    class Guard {

        private OrgAccessGuard guard;

        @BeforeEach
        void setUp() {
            guard = new OrgAccessGuard(memberRepo);
        }

        @Test
        void requireMemberReturnsMembership() {
            OrganizationMember m = member("u1", OrgRole.MEMBER);
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "u1")).thenReturn(Optional.of(m));

            assertThat(guard.requireMember(orgId, "u1")).isSameAs(m);
        }

        @Test
        void nonMemberIsForbidden() {
            when(memberRepo.findByOrganizationIdAndUserId(any(), any())).thenReturn(Optional.empty());

            assertApi(() -> guard.requireMember(orgId, "stranger"), HttpStatus.FORBIDDEN);
        }

        @Test
        void requireRoleChecksAllowedSet() {
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "u")).thenReturn(Optional.of(member("u", OrgRole.ADMIN)));

            assertThat(guard.requireRole(orgId, "u", OrgRole.OWNER, OrgRole.ADMIN).getRole()).isEqualTo(OrgRole.ADMIN);
            assertApi(() -> guard.requireRole(orgId, "u", OrgRole.OWNER), HttpStatus.FORBIDDEN);
        }

        @Test
        void requireManagerAcceptsOwnerAndAdminButNotMember() {
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "o")).thenReturn(Optional.of(member("o", OrgRole.OWNER)));
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "a")).thenReturn(Optional.of(member("a", OrgRole.ADMIN)));
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "m")).thenReturn(Optional.of(member("m", OrgRole.MEMBER)));

            guard.requireManager(orgId, "o");
            guard.requireManager(orgId, "a");
            assertApi(() -> guard.requireManager(orgId, "m"), HttpStatus.FORBIDDEN);
        }

        @Test
        void roleOfIfMemberIsEmptyForStrangers() {
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "x")).thenReturn(Optional.empty());
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "y")).thenReturn(Optional.of(member("y", OrgRole.MEMBER)));

            assertThat(guard.roleOfIfMember(orgId, "x")).isEmpty();
            assertThat(guard.roleOfIfMember(orgId, "y")).contains(OrgRole.MEMBER);
        }
    }

    // ------------------------------------------------------------------ OrganizationService

    @Nested
    class Organizations {

        @Mock OrgAccessGuard guard;
        private OrganizationService service;

        @BeforeEach
        void setUp() {
            service = new OrganizationService(orgRepo, memberRepo, guard, publisher);
        }

        private Organization org(Instant deletedAt) {
            return Organization.builder().id(orgId).name("Acme").slug("acme").ownerUserId("owner").deletedAt(deletedAt).build();
        }

        @Test
        void createSavesOrgOwnerMembershipAndPublishes() {
            when(orgRepo.existsBySlug("acme")).thenReturn(false);
            when(orgRepo.save(any(Organization.class))).thenAnswer(i -> {
                Organization o = i.getArgument(0);
                o.setId(orgId);
                return o;
            });

            Organization o = service.create(new CreateOrganizationRequest("Acme", "acme", "desc"), "owner");

            assertThat(o.getOwnerUserId()).isEqualTo("owner");
            ArgumentCaptor<OrganizationMember> cap = ArgumentCaptor.forClass(OrganizationMember.class);
            verify(memberRepo).save(cap.capture());
            assertThat(cap.getValue().getRole()).isEqualTo(OrgRole.OWNER);
            assertThat(cap.getValue().getOrganizationId()).isEqualTo(orgId);
            assertThat(cap.getValue().getInvitedBy()).isEqualTo("owner");
            verify(publisher).organizationCreated(o, "owner");
        }

        @Test
        void createRejectsDuplicateSlug() {
            when(orgRepo.existsBySlug("acme")).thenReturn(true);

            assertApi(() -> service.create(new CreateOrganizationRequest("Acme", "acme", null), "owner"), HttpStatus.CONFLICT);
            verify(orgRepo, never()).save(any());
            verifyNoInteractions(publisher);
        }

        @Test
        void getRequiresMembershipAndHidesDeletedOrgs() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org(null)), Optional.of(org(Instant.now())));

            assertThat(service.get(orgId, "u").getSlug()).isEqualTo("acme");
            verify(guard).requireMember(orgId, "u");
            assertApi(() -> service.get(orgId, "u"), HttpStatus.NOT_FOUND);
        }

        @Test
        void getUnknownOrg() {
            when(orgRepo.findById(any())).thenReturn(Optional.empty());

            assertApi(() -> service.get(orgId, "u"), HttpStatus.NOT_FOUND);
        }

        @Test
        void listForUserFiltersSoftDeleted() {
            UUID other = UUID.randomUUID();
            when(memberRepo.findByUserId("u")).thenReturn(List.of(member("u", OrgRole.MEMBER),
                    OrganizationMember.builder().organizationId(other).userId("u").role(OrgRole.ADMIN).build()));
            Organization live = org(null);
            Organization dead = Organization.builder().id(other).deletedAt(Instant.now()).build();
            when(orgRepo.findAllById(List.of(orgId, other))).thenReturn(List.of(live, dead));

            assertThat(service.listForUser("u")).containsExactly(live);
        }

        @Test
        void roleOfDelegatesToGuard() {
            when(guard.requireMember(orgId, "u")).thenReturn(member("u", OrgRole.ADMIN));

            assertThat(service.roleOf(orgId, "u")).isEqualTo(OrgRole.ADMIN);
        }

        @Test
        void deleteIsOwnerOnlyAndSoftDeletes() {
            Organization o = org(null);
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(o));

            service.delete(orgId, "owner");

            verify(guard).requireRole(orgId, "owner", OrgRole.OWNER);
            assertThat(o.getDeletedAt()).isNotNull();
            verify(orgRepo).save(o);
        }

        @Test
        void deleteByNonOwnerPropagatesForbidden() {
            when(guard.requireRole(any(), any(), any(OrgRole[].class))).thenThrow(new ApiException("no", HttpStatus.FORBIDDEN));

            assertApi(() -> service.delete(orgId, "admin"), HttpStatus.FORBIDDEN);
            verify(orgRepo, never()).save(any());
        }

        @Test
        void addMemberHappyPath() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org(null)));
            when(memberRepo.existsByOrganizationIdAndUserId(orgId, "new")).thenReturn(false);
            when(memberRepo.save(any(OrganizationMember.class))).thenAnswer(i -> i.getArgument(0));

            OrganizationMember m = service.addMember(orgId, new AddMemberRequest("new", OrgRole.ADMIN), "owner");

            assertThat(m.getRole()).isEqualTo(OrgRole.ADMIN);
            assertThat(m.getInvitedBy()).isEqualTo("owner");
            verify(guard).requireRole(orgId, "owner", OrgRole.OWNER, OrgRole.ADMIN);
            verify(publisher).memberAdded(orgId, "new", "ADMIN", "owner");
        }

        @Test
        void addMemberRejectsDuplicatesAndOwnerRole() {
            when(orgRepo.findById(orgId)).thenReturn(Optional.of(org(null)));
            when(memberRepo.existsByOrganizationIdAndUserId(orgId, "dup")).thenReturn(true);
            when(memberRepo.existsByOrganizationIdAndUserId(orgId, "boss")).thenReturn(false);

            assertApi(() -> service.addMember(orgId, new AddMemberRequest("dup", OrgRole.MEMBER), "o"), HttpStatus.CONFLICT);
            assertApi(() -> service.addMember(orgId, new AddMemberRequest("boss", OrgRole.OWNER), "o"), HttpStatus.BAD_REQUEST);
            verify(memberRepo, never()).save(any());
        }

        @Test
        void removeMemberDeletesAndPublishes() {
            OrganizationMember target = member("t", OrgRole.MEMBER);
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "t")).thenReturn(Optional.of(target));

            service.removeMember(orgId, "t", "admin");

            verify(memberRepo).delete(target);
            verify(publisher).memberRemoved(orgId, "t", "admin");
        }

        @Test
        void cannotRemoveOwnerOrUnknownMember() {
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "owner")).thenReturn(Optional.of(member("owner", OrgRole.OWNER)));
            when(memberRepo.findByOrganizationIdAndUserId(orgId, "ghost")).thenReturn(Optional.empty());

            assertApi(() -> service.removeMember(orgId, "owner", "admin"), HttpStatus.BAD_REQUEST);
            assertApi(() -> service.removeMember(orgId, "ghost", "admin"), HttpStatus.NOT_FOUND);
            verify(memberRepo, never()).delete(any());
        }

        @Test
        void listMembersRequiresMembership() {
            List<OrganizationMember> members = List.of(member("a", OrgRole.OWNER));
            when(memberRepo.findByOrganizationId(orgId)).thenReturn(members);

            assertThat(service.listMembers(orgId, "a")).isSameAs(members);
            verify(guard).requireMember(orgId, "a");
        }
    }

    // ------------------------------------------------------------------ ProjectService

    @Nested
    class Projects {

        @Mock OrgAccessGuard guard;
        private ProjectService service;

        @BeforeEach
        void setUp() {
            service = new ProjectService(projectRepo, envRepo, guard, publisher);
        }

        private Project project(Instant deletedAt, UUID org) {
            return Project.builder().id(projectId).organizationId(org).slug("web").name("Web").deletedAt(deletedAt).build();
        }

        @Test
        void createSeedsThreeEnvironmentsWithMainBranchOnProduction() {
            when(projectRepo.existsByOrganizationIdAndSlug(orgId, "web")).thenReturn(false);
            when(projectRepo.save(any(Project.class))).thenAnswer(i -> {
                Project p = i.getArgument(0);
                p.setId(projectId);
                return p;
            });

            Project p = service.create(orgId, new CreateProjectRequest("Web", "web", "d"), "admin");

            verify(guard).requireManager(orgId, "admin");
            ArgumentCaptor<ProjectEnvironment> cap = ArgumentCaptor.forClass(ProjectEnvironment.class);
            verify(envRepo, times(3)).save(cap.capture());
            assertThat(cap.getAllValues()).extracting(ProjectEnvironment::getEnvironment)
                    .containsExactly(EnvironmentType.DEVELOPMENT, EnvironmentType.STAGING, EnvironmentType.PRODUCTION);
            assertThat(cap.getAllValues()).extracting(ProjectEnvironment::getTrackedBranch).containsExactly(null, null, "main");
            verify(publisher).projectCreated(p, "admin");
        }

        @Test
        void createRejectsDuplicateSlug() {
            when(projectRepo.existsByOrganizationIdAndSlug(orgId, "web")).thenReturn(true);

            assertApi(() -> service.create(orgId, new CreateProjectRequest("Web", "web", null), "admin"), HttpStatus.CONFLICT);
            verify(projectRepo, never()).save(any());
        }

        @Test
        void getEnforcesTenantIsolation() {
            when(projectRepo.findById(projectId)).thenReturn(
                    Optional.of(project(null, orgId)),
                    Optional.of(project(null, UUID.randomUUID())),
                    Optional.of(project(Instant.now(), orgId)));

            assertThat(service.get(orgId, projectId, "u").getSlug()).isEqualTo("web");
            assertApi(() -> service.get(orgId, projectId, "u"), HttpStatus.NOT_FOUND);
            assertApi(() -> service.get(orgId, projectId, "u"), HttpStatus.NOT_FOUND);
        }

        @Test
        void getUnknownProject() {
            when(projectRepo.findById(any())).thenReturn(Optional.empty());

            assertApi(() -> service.get(orgId, projectId, "u"), HttpStatus.NOT_FOUND);
        }

        @Test
        void listAndEnvironments() {
            when(projectRepo.findByOrganizationIdAndDeletedAtIsNull(orgId)).thenReturn(List.of(project(null, orgId)));
            when(projectRepo.findById(projectId)).thenReturn(Optional.of(project(null, orgId)));
            when(envRepo.findByProjectId(projectId)).thenReturn(List.of(ProjectEnvironment.builder().build()));

            assertThat(service.list(orgId, "u")).hasSize(1);
            assertThat(service.environments(orgId, projectId, "u")).hasSize(1);
            verify(guard, times(2)).requireMember(orgId, "u");
        }

        @Test
        void deleteSoftDeletesAndPublishes() {
            Project p = project(null, orgId);
            when(projectRepo.findById(projectId)).thenReturn(Optional.of(p));

            service.delete(orgId, projectId, "admin");

            assertThat(p.getDeletedAt()).isNotNull();
            verify(projectRepo).save(p);
            verify(publisher).projectDeleted(projectId, orgId, "admin");
            verify(guard).requireManager(orgId, "admin");
        }
    }

    // ------------------------------------------------------------------ SecretService + crypto

    @Nested
    class Secrets {

        @Mock OrgAccessGuard guard;
        @Mock ProjectService projectService;
        @Mock SecretCryptoService crypto;
        private SecretService service;

        @BeforeEach
        void setUp() {
            service = new SecretService(secretRepo, guard, projectService, crypto, publisher);
        }

        @Test
        void createEncryptsAndNeverStoresPlaintext() {
            when(secretRepo.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(any(), any(), any())).thenReturn(Optional.empty());
            when(crypto.encrypt("hunter2")).thenReturn("CIPHER");
            when(crypto.currentKeyVersion()).thenReturn("kek-v1");
            when(secretRepo.save(any(Secret.class))).thenAnswer(i -> i.getArgument(0));

            Secret s = service.create(orgId, projectId, EnvironmentType.STAGING, new CreateSecretRequest("DB_PASSWORD", "hunter2"), "admin");

            assertThat(s.getEncryptedValue()).isEqualTo("CIPHER");
            assertThat(s.getEncryptionKeyVersion()).isEqualTo("kek-v1");
            assertThat(s.getVersion()).isEqualTo(1);
            assertThat(s.isRevoked()).isFalse();
            assertThat(s.toString()).doesNotContain("hunter2");
            verify(publisher).secretCreated(projectId, EnvironmentType.STAGING, "DB_PASSWORD", "admin");
            verify(guard).requireManager(orgId, "admin");
            verify(projectService).findActiveOrThrow(orgId, projectId);
        }

        @Test
        void createRejectsExistingActiveKey() {
            when(secretRepo.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(any(), any(), eq("K")))
                    .thenReturn(Optional.of(Secret.builder().build()));

            assertApi(() -> service.create(orgId, projectId, EnvironmentType.STAGING, new CreateSecretRequest("K", "v"), "a"), HttpStatus.CONFLICT);
            verify(secretRepo, never()).save(any());
            verifyNoInteractions(crypto);
        }

        @Test
        void revokeMarksRevoked() {
            Secret s = Secret.builder().secretKey("K").build();
            when(secretRepo.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, EnvironmentType.PRODUCTION, "K"))
                    .thenReturn(Optional.of(s));

            service.revoke(orgId, projectId, EnvironmentType.PRODUCTION, "K", "admin");

            assertThat(s.isRevoked()).isTrue();
            assertThat(s.getRevokedAt()).isNotNull();
            verify(publisher).secretRevoked(projectId, EnvironmentType.PRODUCTION, "K", "admin");
        }

        @Test
        void revokeMissingIs404() {
            when(secretRepo.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(any(), any(), any())).thenReturn(Optional.empty());

            assertApi(() -> service.revoke(orgId, projectId, EnvironmentType.PRODUCTION, "K", "a"), HttpStatus.NOT_FOUND);
        }

        @Test
        void listReturnsOnlyActiveForManagers() {
            List<Secret> active = List.of(Secret.builder().secretKey("A").build());
            when(secretRepo.findByProjectIdAndEnvironmentAndRevokedFalse(projectId, EnvironmentType.STAGING)).thenReturn(active);

            assertThat(service.list(orgId, projectId, EnvironmentType.STAGING, "admin")).isSameAs(active);
            verify(guard).requireManager(orgId, "admin");
        }

        @Test
        void listDeniedForNonManagers() {
            when(guard.requireManager(any(), any())).thenThrow(new ApiException("no", HttpStatus.FORBIDDEN));

            assertApi(() -> service.list(orgId, projectId, EnvironmentType.STAGING, "member"), HttpStatus.FORBIDDEN);
            verifyNoInteractions(secretRepo);
        }

        @Test
        void resolvePlaintextDecryptsWithoutAuthorisationCheck() {
            when(secretRepo.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(projectId, EnvironmentType.STAGING, "K"))
                    .thenReturn(Optional.of(Secret.builder().encryptedValue("CIPHER").build()));
            when(crypto.decrypt("CIPHER")).thenReturn("plain");

            assertThat(service.resolvePlaintext(projectId, EnvironmentType.STAGING, "K")).isEqualTo("plain");
            verifyNoInteractions(guard);
        }

        @Test
        void resolvePlaintextMissing() {
            when(secretRepo.findByProjectIdAndEnvironmentAndSecretKeyAndRevokedFalse(any(), any(), any())).thenReturn(Optional.empty());

            assertApi(() -> service.resolvePlaintext(projectId, EnvironmentType.STAGING, "K"), HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void secretCryptoRoundTripAndTamperDetection() {
        SecretCryptoService crypto = new SecretCryptoService(Base64.getEncoder().encodeToString(new byte[32]));

        String enc = crypto.encrypt("s3cr3t ⚡");

        assertThat(enc).doesNotContain("s3cr3t");
        assertThat(crypto.decrypt(enc)).isEqualTo("s3cr3t ⚡");
        assertThat(crypto.encrypt("s3cr3t ⚡")).isNotEqualTo(enc);
        assertThat(crypto.currentKeyVersion()).isEqualTo("kek-v1");

        byte[] raw = Base64.getDecoder().decode(enc);
        raw[raw.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(raw))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> crypto.decrypt("not base64 !!")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretCryptoRequires32ByteKek() {
        assertThatThrownBy(() -> new SecretCryptoService(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void dtoMappingsNeverExposeSecretValues() {
        Secret s = Secret.builder().id(UUID.randomUUID()).secretKey("K").version(2).encryptedValue("CIPHER").createdAt(Instant.now()).build();

        SecretResponse r = SecretResponse.from(s);

        assertThat(r.maskedValue()).isEqualTo("••••••••");
        assertThat(r.toString()).doesNotContain("CIPHER");
        assertThat(MemberResponse.from(member("u", OrgRole.ADMIN)).role()).isEqualTo("ADMIN");
        assertThat(OrganizationResponse.from(Organization.builder().slug("a").build(), OrgRole.OWNER).callerRole()).isEqualTo(OrgRole.OWNER);
        assertThat(ProjectResponse.from(Project.builder().slug("p").build()).slug()).isEqualTo("p");
        assertThat(ModuleEnablementResponse.from(ModuleEnablement.builder().module(ModuleType.CACHE)
                .environment(EnvironmentType.STAGING).status(ModuleStatus.ACTIVE).build()).module()).isEqualTo("CACHE");
    }
}
