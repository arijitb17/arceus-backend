package com.arijit.featureflagservice.service;

import com.arijit.featureflagservice.FlagTestData;
import com.arijit.featureflagservice.dto.CreateFlagRequest;
import com.arijit.featureflagservice.dto.EvaluationResult;
import com.arijit.featureflagservice.dto.UpdateFlagRequest;
import com.arijit.featureflagservice.entity.*;
import com.arijit.featureflagservice.exception.ApiException;
import com.arijit.featureflagservice.messaging.FlagEventPublisher;
import com.arijit.featureflagservice.repository.FeatureFlagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock FeatureFlagRepository repo;
    @Mock FlagVersionService versionService;
    @Mock FlagAuditService auditService;
    @Mock FlagCacheService cacheService;
    @Mock FlagEventPublisher eventPublisher;
    @Mock FlagSchedulingService schedulingService;
    @Mock RolloutEvaluator rollout;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private FlagService service;

    @BeforeEach
    void setUp() {
        service = new FlagService(repo, new FlagValidationService(), new FlagValueCodec(mapper), versionService,
                auditService, cacheService, eventPublisher, schedulingService, rollout, mapper);
        lenient().when(repo.save(any(FeatureFlag.class))).thenAnswer(i -> {
            FeatureFlag f = i.getArgument(0);
            if (f.getId() == null) f.setId(java.util.UUID.randomUUID());
            return f;
        });
    }

    private CreateFlagRequest create(String key, Environment env, TargetType target, Object value, Instant on, Instant off) {
        return new CreateFlagRequest(key, "Name", "desc", FlagType.BOOLEAN, env, target, value, null, on, off, "alice");
    }

    private void assertApi(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Nested
    class Create {

        @Test
        void newFlagStartsDisabledWithDefaults() {
            when(repo.existsByFlagKeyAndEnvironment("f", Environment.PRODUCTION)).thenReturn(false);

            FeatureFlag f = service.create(create("f", Environment.PRODUCTION, null, true, null, null));

            assertThat(f.getStatus()).isEqualTo(FlagStatus.DISABLED);
            assertThat(f.getTargetType()).isEqualTo(TargetType.ENVIRONMENT);
            assertThat(f.getRolloutPercentage()).isEqualTo(100);
            assertThat(f.getValueBoolean()).isTrue();
            assertThat(f.getCreatedBy()).isEqualTo("alice");
            verify(versionService).record(f, FlagVersion.ChangeType.CREATE, "alice");
            verify(auditService).record(eq(f.getId()), eq("f"), eq(Environment.PRODUCTION), eq("CREATE"), eq("alice"), isNull(), eq("true"));
            verify(eventPublisher).created(f, "alice");
            verifyNoInteractions(schedulingService);
        }

        @Test
        void globalTargetForcesGlobalEnvironment() {
            when(repo.existsByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(false);

            FeatureFlag f = service.create(create("f", null, TargetType.GLOBAL, false, null, null));

            assertThat(f.getEnvironment()).isEqualTo(Environment.GLOBAL);
        }

        @Test
        void blankActorBecomesUnknown() {
            when(repo.existsByFlagKeyAndEnvironment(anyString(), any())).thenReturn(false);
            CreateFlagRequest r = new CreateFlagRequest("f", "n", null, FlagType.BOOLEAN, Environment.STAGING, null, true, 40, null, null, "  ");

            FeatureFlag f = service.create(r);

            assertThat(f.getCreatedBy()).isEqualTo("unknown");
            assertThat(f.getRolloutPercentage()).isEqualTo(40);
        }

        @Test
        void duplicateKeyInEnvironmentConflicts() {
            when(repo.existsByFlagKeyAndEnvironment("f", Environment.STAGING)).thenReturn(true);

            assertApi(() -> service.create(create("f", Environment.STAGING, null, true, null, null)), HttpStatus.CONFLICT);
            verify(repo, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void validationRunsFirst() {
            assertApi(() -> service.create(create("f", Environment.STAGING, TargetType.USER, true, null, null)), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(repo);
        }

        @Test
        void schedulesActivationAndDeactivationAndStoresJobIds() {
            Instant on = Instant.now().plus(1, ChronoUnit.HOURS);
            Instant off = on.plus(1, ChronoUnit.HOURS);
            when(repo.existsByFlagKeyAndEnvironment(anyString(), any())).thenReturn(false);
            when(schedulingService.scheduleActivation(any())).thenReturn("job-on");
            when(schedulingService.scheduleDeactivation(any())).thenReturn("job-off");

            FeatureFlag f = service.create(create("f", Environment.STAGING, null, true, on, off));

            assertThat(f.getActivateJobId()).isEqualTo("job-on");
            assertThat(f.getDeactivateJobId()).isEqualTo("job-off");
            verify(repo, times(2)).save(f);
        }
    }

    @Nested
    class Query {

        @Test
        void getOrThrow() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));
            when(repo.findByFlagKeyAndEnvironment("x", Environment.GLOBAL)).thenReturn(Optional.empty());

            assertThat(service.getOrThrow("f", Environment.GLOBAL)).isSameAs(f);
            assertApi(() -> service.getOrThrow("x", Environment.GLOBAL), HttpStatus.NOT_FOUND);
        }

        @Test
        void listPicksRepositoryMethodByFilters() {
            Pageable p = PageRequest.of(0, 10);
            when(repo.findByEnvironmentAndStatus(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
            when(repo.findByEnvironment(any(), any())).thenReturn(new PageImpl<>(List.of()));
            when(repo.findByStatus(any(), any())).thenReturn(new PageImpl<>(List.of()));
            when(repo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

            service.list(Environment.STAGING, FlagStatus.ENABLED, p);
            service.list(Environment.STAGING, null, p);
            service.list(null, FlagStatus.DISABLED, p);
            service.list(null, null, p);

            verify(repo).findByEnvironmentAndStatus(Environment.STAGING, FlagStatus.ENABLED, p);
            verify(repo).findByEnvironment(Environment.STAGING, p);
            verify(repo).findByStatus(FlagStatus.DISABLED, p);
            verify(repo).findAll(p);
        }
    }

    @Nested
    class Update {

        private FeatureFlag existing;

        @BeforeEach
        void setUp() {
            existing = FlagTestData.booleanFlag("f", Environment.STAGING, FlagStatus.ENABLED, true);
            lenient().when(repo.findByFlagKeyAndEnvironment("f", Environment.STAGING)).thenReturn(Optional.of(existing));
        }

        @Test
        void appliesOnlyProvidedFieldsAndEvictsCache() {
            UpdateFlagRequest r = new UpdateFlagRequest("New name", null, false, 25, null, null, "bob");

            FeatureFlag f = service.update("f", Environment.STAGING, r);

            assertThat(f.getName()).isEqualTo("New name");
            assertThat(f.getDescription()).isNull();
            assertThat(f.getValueBoolean()).isFalse();
            assertThat(f.getRolloutPercentage()).isEqualTo(25);
            assertThat(f.getUpdatedBy()).isEqualTo("bob");
            verify(cacheService).evict("f", Environment.STAGING);
            verify(versionService).record(f, FlagVersion.ChangeType.UPDATE, "bob");
            verify(auditService).record(eq(f.getId()), eq("f"), eq(Environment.STAGING), eq("UPDATE"), eq("bob"), eq("true"), eq("false"));
            verify(eventPublisher).updated(f, "bob");
            verifyNoInteractions(schedulingService);
        }

        @Test
        void unchangedScheduleDoesNotReschedule() {
            Instant on = Instant.now().plusSeconds(3600);
            existing.setActivateAt(on);

            service.update("f", Environment.STAGING, new UpdateFlagRequest(null, null, null, null, on, null, "bob"));

            verifyNoInteractions(schedulingService);
        }

        @Test
        void changedScheduleCancelsOldJobsAndSchedulesNew() {
            existing.setActivateJobId("old-on");
            existing.setDeactivateJobId("old-off");
            Instant on = Instant.now().plusSeconds(3600);
            when(schedulingService.scheduleActivation(any())).thenReturn("new-on");

            FeatureFlag f = service.update("f", Environment.STAGING, new UpdateFlagRequest(null, null, null, null, on, null, "bob"));

            verify(schedulingService).cancel("old-on");
            verify(schedulingService).cancel("old-off");
            assertThat(f.getActivateJobId()).isEqualTo("new-on");
            assertThat(f.getDeactivateJobId()).isNull();
        }

        @Test
        void invalidScheduleRejectedBeforeLookup() {
            Instant on = Instant.now().plusSeconds(100);

            assertApi(() -> service.update("f", Environment.STAGING,
                    new UpdateFlagRequest(null, null, null, null, on, on.minusSeconds(5), "bob")), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(repo);
        }

        @Test
        void unknownFlagIs404() {
            assertApi(() -> service.update("missing", Environment.STAGING,
                    new UpdateFlagRequest(null, null, null, null, null, null, null)), HttpStatus.NOT_FOUND);
        }

        @Test
        void wrongValueTypeIs400() {
            assertApi(() -> service.update("f", Environment.STAGING,
                    new UpdateFlagRequest(null, null, "banana", null, null, null, "bob")), HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class DeleteAndStatus {

        @Test
        void deleteCancelsJobsRecordsAndRemoves() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            f.setActivateJobId("a");
            f.setDeactivateJobId("d");
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));

            service.delete("f", Environment.GLOBAL, "carol");

            verify(schedulingService).cancel("a");
            verify(schedulingService).cancel("d");
            verify(versionService).record(f, FlagVersion.ChangeType.DELETE, "carol");
            verify(cacheService).evict("f", Environment.GLOBAL);
            verify(repo).delete(f);
            verify(eventPublisher).deleted(f, "carol");
        }

        @Test
        void deleteUnknown() {
            when(repo.findByFlagKeyAndEnvironment(anyString(), any())).thenReturn(Optional.empty());

            assertApi(() -> service.delete("x", Environment.GLOBAL, null), HttpStatus.NOT_FOUND);
            verify(repo, never()).delete(any());
        }

        @Test
        void enableDisabledFlag() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.DISABLED, true);
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));

            FeatureFlag r = service.enable("f", Environment.GLOBAL, "dave");

            assertThat(r.getStatus()).isEqualTo(FlagStatus.ENABLED);
            verify(cacheService).evict("f", Environment.GLOBAL);
            verify(versionService).record(f, FlagVersion.ChangeType.ENABLE, "dave");
            verify(auditService).record(f.getId(), "f", Environment.GLOBAL, "ENABLED", "dave", null, null);
            verify(eventPublisher).enabled(f, "dave");
        }

        @Test
        void disableEnabledFlag() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));

            service.disable("f", Environment.GLOBAL, null);

            assertThat(f.getStatus()).isEqualTo(FlagStatus.DISABLED);
            assertThat(f.getUpdatedBy()).isEqualTo("unknown");
            verify(eventPublisher).disabled(f, "unknown");
        }

        @Test
        void settingSameStatusIsNoOp() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));

            service.enable("f", Environment.GLOBAL, "x");

            verifyNoInteractions(cacheService, versionService, auditService, eventPublisher);
            verify(repo, never()).save(any());
        }

        @Test
        void scheduledActivationEnablesAndClearsJobId() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.STAGING, FlagStatus.DISABLED, true);
            f.setActivateJobId("job");
            when(repo.findByFlagKeyAndEnvironment("f", Environment.STAGING)).thenReturn(Optional.of(f));

            FeatureFlag r = service.applyScheduledActivation("f", Environment.STAGING);

            assertThat(r.getStatus()).isEqualTo(FlagStatus.ENABLED);
            assertThat(r.getActivateJobId()).isNull();
            assertThat(r.getUpdatedBy()).isEqualTo("scheduler-service");
        }

        @Test
        void scheduledDeactivationDisablesAndClearsJobId() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.STAGING, FlagStatus.ENABLED, true);
            f.setDeactivateJobId("job");
            when(repo.findByFlagKeyAndEnvironment("f", Environment.STAGING)).thenReturn(Optional.of(f));

            FeatureFlag r = service.applyScheduledDeactivation("f", Environment.STAGING);

            assertThat(r.getStatus()).isEqualTo(FlagStatus.DISABLED);
            assertThat(r.getDeactivateJobId()).isNull();
        }
    }

    @Nested
    class Evaluate {

        private String cacheJson(FlagStatus status, int rollout, String valueJson) throws Exception {
            return mapper.writeValueAsString(new FlagCacheEntry("f", FlagType.BOOLEAN, status, rollout, valueJson));
        }

        @Test
        void unknownFlagReturnsCallerDefault() {
            when(cacheService.get("f", Environment.GLOBAL)).thenReturn(null);
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.empty());

            EvaluationResult r = service.evaluate("f", Environment.GLOBAL, null, "dflt");

            assertThat(r.found()).isFalse();
            assertThat(r.enabled()).isFalse();
            assertThat(r.value()).isEqualTo("dflt");
        }

        @Test
        void dbHitPopulatesCacheAndReturnsStoredValue() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            when(cacheService.get("f", Environment.GLOBAL)).thenReturn(null);
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));
            when(rollout.isIncluded(100, "f", "u1")).thenReturn(true);

            EvaluationResult r = service.evaluate("f", Environment.GLOBAL, "u1", false);

            assertThat(r.found()).isTrue();
            assertThat(r.enabled()).isTrue();
            assertThat(r.value()).isEqualTo(true);
            assertThat(r.fromCache()).isFalse();
            assertThat(r.type()).isEqualTo(FlagType.BOOLEAN);
            ArgumentCaptor<String> cached = ArgumentCaptor.forClass(String.class);
            verify(cacheService).put(eq("f"), eq(Environment.GLOBAL), cached.capture());
            assertThat(cached.getValue()).contains("\"status\":\"ENABLED\"").contains("\"valueJson\":\"true\"");
        }

        @Test
        void cacheHitSkipsDatabase() throws Exception {
            when(cacheService.get("f", Environment.GLOBAL)).thenReturn(cacheJson(FlagStatus.ENABLED, 100, "true"));
            when(rollout.isIncluded(anyInt(), anyString(), any())).thenReturn(true);

            EvaluationResult r = service.evaluate("f", Environment.GLOBAL, null, false);

            assertThat(r.fromCache()).isTrue();
            assertThat(r.value()).isEqualTo(true);
            verifyNoInteractions(repo);
            verify(cacheService, never()).put(anyString(), any(), anyString());
        }

        @Test
        void corruptCacheEntryFallsBackToDatabase() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            when(cacheService.get("f", Environment.GLOBAL)).thenReturn("{not json");
            when(repo.findByFlagKeyAndEnvironment("f", Environment.GLOBAL)).thenReturn(Optional.of(f));
            when(rollout.isIncluded(anyInt(), anyString(), any())).thenReturn(true);

            EvaluationResult r = service.evaluate("f", Environment.GLOBAL, null, false);

            assertThat(r.found()).isTrue();
            assertThat(r.fromCache()).isFalse();
        }

        @Test
        void disabledFlagYieldsDefaultButFound() throws Exception {
            when(cacheService.get("f", Environment.GLOBAL)).thenReturn(cacheJson(FlagStatus.DISABLED, 100, "true"));

            EvaluationResult r = service.evaluate("f", Environment.GLOBAL, null, "fallback");

            assertThat(r.found()).isTrue();
            assertThat(r.enabled()).isFalse();
            assertThat(r.value()).isEqualTo("fallback");
            verifyNoInteractions(rollout);
        }

        @Test
        void outsideRolloutYieldsDefault() throws Exception {
            when(cacheService.get("f", Environment.GLOBAL)).thenReturn(cacheJson(FlagStatus.ENABLED, 10, "true"));
            when(rollout.isIncluded(10, "f", "user-9")).thenReturn(false);

            EvaluationResult r = service.evaluate("f", Environment.GLOBAL, "user-9", "fallback");

            assertThat(r.enabled()).isFalse();
            assertThat(r.value()).isEqualTo("fallback");
        }
    }
}
