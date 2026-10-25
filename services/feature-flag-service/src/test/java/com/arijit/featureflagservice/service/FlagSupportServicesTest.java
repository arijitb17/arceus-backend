package com.arijit.featureflagservice.service;

import com.arijit.arceus.grpc.cache.*;
import com.arijit.arceus.grpc.scheduler.JobIdRequest;
import com.arijit.arceus.grpc.scheduler.Job;
import com.arijit.arceus.grpc.scheduler.ScheduleJobRequest;
import com.arijit.arceus.grpc.scheduler.ScheduleJobResponse;
import com.arijit.arceus.grpc.scheduler.SchedulerGrpcServiceGrpc;
import com.arijit.featureflagservice.FlagTestData;
import com.arijit.featureflagservice.entity.*;
import com.arijit.featureflagservice.repository.FlagAuditLogRepository;
import com.arijit.featureflagservice.repository.FlagVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagSupportServicesTest {

    @Mock CacheGrpcServiceGrpc.CacheGrpcServiceBlockingStub cacheStub;
    @Mock SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub schedulerStub;
    @Mock FlagAuditLogRepository auditRepo;
    @Mock FlagVersionRepository versionRepo;

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Nested
    class Cache {

        @Test
        void disabledCacheIsAlwaysAMissAndNeverCalls() {
            FlagCacheService svc = new FlagCacheService(cacheStub, FlagTestData.properties(false, 60, true));

            assertThat(svc.get("f", Environment.GLOBAL)).isNull();
            svc.put("f", Environment.GLOBAL, "x");
            svc.evict("f", Environment.GLOBAL);

            verifyNoInteractions(cacheStub);
        }

        @Test
        void getReturnsValueOnlyWhenFound() {
            FlagCacheService svc = new FlagCacheService(cacheStub, FlagTestData.properties(true, 60, true));
            when(cacheStub.get(any())).thenReturn(
                    GetResponse.newBuilder().setFound(true).setValue("payload").build(),
                    GetResponse.newBuilder().setFound(false).setValue("stale").build());

            assertThat(svc.get("f", Environment.STAGING)).isEqualTo("payload");
            assertThat(svc.get("f", Environment.STAGING)).isNull();

            ArgumentCaptor<CacheKeyRef> cap = ArgumentCaptor.forClass(CacheKeyRef.class);
            verify(cacheStub, times(2)).get(cap.capture());
            assertThat(cap.getValue().getNamespace()).isEqualTo("featureflag");
            assertThat(cap.getValue().getKey()).isEqualTo("STAGING:f");
        }

        @Test
        void putStoresWithCustomTtl() {
            FlagCacheService svc = new FlagCacheService(cacheStub, FlagTestData.properties(true, 45, true));

            svc.put("f", Environment.GLOBAL, "json");

            ArgumentCaptor<PutRequest> cap = ArgumentCaptor.forClass(PutRequest.class);
            verify(cacheStub).put(cap.capture());
            assertThat(cap.getValue().getValue()).isEqualTo("json");
            assertThat(cap.getValue().getSerialization()).isEqualTo("STRING");
            assertThat(cap.getValue().getTtlPolicy()).isEqualTo("CUSTOM");
            assertThat(cap.getValue().getTtlSeconds()).isEqualTo(45);
        }

        @Test
        void putSkippedForZeroTtlOrNullValue() {
            new FlagCacheService(cacheStub, FlagTestData.properties(true, 0, true)).put("f", Environment.GLOBAL, "x");
            new FlagCacheService(cacheStub, FlagTestData.properties(true, 10, true)).put("f", Environment.GLOBAL, null);

            verifyNoInteractions(cacheStub);
        }

        @Test
        void evictDeletesNamespacedKey() {
            FlagCacheService svc = new FlagCacheService(cacheStub, FlagTestData.properties(true, 60, true));
            when(cacheStub.delete(any())).thenReturn(DeleteResponse.newBuilder().setDeleted(false).build());

            svc.evict("f", Environment.PRODUCTION);

            ArgumentCaptor<CacheKeyRef> cap = ArgumentCaptor.forClass(CacheKeyRef.class);
            verify(cacheStub).delete(cap.capture());
            assertThat(cap.getValue().getKey()).isEqualTo("PRODUCTION:f");
        }

        @Test
        void allOperationsSwallowCacheServiceOutages() {
            FlagCacheService svc = new FlagCacheService(cacheStub, FlagTestData.properties(true, 60, true));
            when(cacheStub.get(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(cacheStub.put(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
            when(cacheStub.delete(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThat(svc.get("f", Environment.GLOBAL)).isNull();
            assertThatCode(() -> {
                svc.put("f", Environment.GLOBAL, "x");
                svc.evict("f", Environment.GLOBAL);
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    class Scheduling {

        private FeatureFlag flag() {
            FeatureFlag f = FlagTestData.booleanFlag("my flag", Environment.STAGING, FlagStatus.DISABLED, true);
            f.setActivateAt(Instant.now().plusSeconds(120));
            f.setDeactivateAt(Instant.now().plusSeconds(600));
            return f;
        }

        @Test
        void activationJobTargetsEncodedCallbackWithToken() {
            FlagSchedulingService svc = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, true));
            when(schedulerStub.scheduleJob(any())).thenReturn(
                    ScheduleJobResponse.newBuilder().setJob(Job.newBuilder().setId("job-1")).build());

            String id = svc.scheduleActivation(flag());

            assertThat(id).isEqualTo("job-1");
            ArgumentCaptor<ScheduleJobRequest> cap = ArgumentCaptor.forClass(ScheduleJobRequest.class);
            verify(schedulerStub).scheduleJob(cap.capture());
            ScheduleJobRequest r = cap.getValue();
            assertThat(r.getJobName()).isEqualTo("feature-flag-activate-my flag-STAGING");
            assertThat(r.getTriggerType()).isEqualTo("DELAY");
            assertThat(r.getExecutionTarget()).isEqualTo("HTTP_CALLBACK");
            assertThat(r.getMaxRetries()).isEqualTo(3);
            assertThat(r.getDelayMs()).isBetween(100_000L, 121_000L);
            assertThat(r.getCallbackUrl()).isEqualTo(
                    "http://ff:8087/internal/flags/my%20flag/scheduled-activate?environment=STAGING&token=internal-token");
        }

        @Test
        void deactivationUsesDeactivatePath() {
            FlagSchedulingService svc = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, true));
            when(schedulerStub.scheduleJob(any())).thenReturn(ScheduleJobResponse.getDefaultInstance());

            svc.scheduleDeactivation(flag());

            ArgumentCaptor<ScheduleJobRequest> cap = ArgumentCaptor.forClass(ScheduleJobRequest.class);
            verify(schedulerStub).scheduleJob(cap.capture());
            assertThat(cap.getValue().getCallbackUrl()).contains("/scheduled-deactivate?");
        }

        @Test
        void pastTimeFiresImmediately() {
            FlagSchedulingService svc = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, true));
            FeatureFlag f = flag();
            f.setActivateAt(Instant.now().minusSeconds(500));
            when(schedulerStub.scheduleJob(any())).thenReturn(ScheduleJobResponse.getDefaultInstance());

            svc.scheduleActivation(f);

            ArgumentCaptor<ScheduleJobRequest> cap = ArgumentCaptor.forClass(ScheduleJobRequest.class);
            verify(schedulerStub).scheduleJob(cap.capture());
            assertThat(cap.getValue().getDelayMs()).isZero();
        }

        @Test
        void disabledSchedulingOrNoTimeReturnsNull() {
            FlagSchedulingService off = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, false));
            FlagSchedulingService on = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, true));
            FeatureFlag noTime = flag();
            noTime.setActivateAt(null);

            assertThat(off.scheduleActivation(flag())).isNull();
            assertThat(on.scheduleActivation(noTime)).isNull();
            verifyNoInteractions(schedulerStub);
        }

        @Test
        void schedulerOutageReturnsNullInsteadOfThrowing() {
            FlagSchedulingService svc = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, true));
            when(schedulerStub.scheduleJob(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

            assertThat(svc.scheduleActivation(flag())).isNull();
        }

        @Test
        void cancelIgnoresBlankIdsAndSwallowsFailures() {
            FlagSchedulingService svc = new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, true));
            svc.cancel(null);
            svc.cancel("  ");
            verifyNoInteractions(schedulerStub);

            svc.cancel("job-9");
            ArgumentCaptor<JobIdRequest> cap = ArgumentCaptor.forClass(JobIdRequest.class);
            verify(schedulerStub).cancelJob(cap.capture());
            assertThat(cap.getValue().getId()).isEqualTo("job-9");

            when(schedulerStub.cancelJob(any())).thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
            assertThatCode(() -> svc.cancel("job-10")).doesNotThrowAnyException();
        }

        @Test
        void cancelDoesNothingWhenSchedulingDisabled() {
            new FlagSchedulingService(schedulerStub, FlagTestData.properties(true, 60, false)).cancel("job");

            verifyNoInteractions(schedulerStub);
        }
    }

    @Nested
    class Audit {

        @Test
        void recordsEntryWithCorrelationIdFromMdc() {
            MDC.put("correlationId", "corr-1");
            UUID id = UUID.randomUUID();

            new FlagAuditService(auditRepo).record(id, "f", Environment.GLOBAL, "UPDATE", "alice", "old", "new");

            ArgumentCaptor<FlagAuditLog> cap = ArgumentCaptor.forClass(FlagAuditLog.class);
            verify(auditRepo).save(cap.capture());
            FlagAuditLog e = cap.getValue();
            assertThat(e.getFlagId()).isEqualTo(id);
            assertThat(e.getActor()).isEqualTo("alice");
            assertThat(e.getOldValue()).isEqualTo("old");
            assertThat(e.getNewValue()).isEqualTo("new");
            assertThat(e.getCorrelationId()).isEqualTo("corr-1");
            assertThat(e.getTimestamp()).isNotNull();
        }

        @Test
        void nullActorBecomesUnknownAndFailuresAreSwallowed() {
            FlagAuditService svc = new FlagAuditService(auditRepo);
            when(auditRepo.save(any())).thenThrow(new IllegalStateException("db down"));

            assertThatCode(() -> svc.record(null, "f", Environment.GLOBAL, "DELETE", null, null, null)).doesNotThrowAnyException();
        }
    }

    @Nested
    class Version {

        @Test
        void snapshotsFlagJsonWithItsVersionNumber() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

            new FlagVersionService(versionRepo, mapper).record(f, FlagVersion.ChangeType.ENABLE, "bob");

            ArgumentCaptor<FlagVersion> cap = ArgumentCaptor.forClass(FlagVersion.class);
            verify(versionRepo).save(cap.capture());
            FlagVersion v = cap.getValue();
            assertThat(v.getFlagId()).isEqualTo(f.getId());
            assertThat(v.getVersionNumber()).isEqualTo(3L);
            assertThat(v.getChangeType()).isEqualTo(FlagVersion.ChangeType.ENABLE);
            assertThat(v.getSnapshot()).contains("\"flagKey\":\"f\"");
            assertThat(v.getChangedBy()).isEqualTo("bob");
        }

        @Test
        void nullVersionBecomesZeroAndFailuresSwallowed() {
            FeatureFlag f = FlagTestData.booleanFlag("f", Environment.GLOBAL, FlagStatus.ENABLED, true);
            f.setVersion(null);
            FlagVersionService svc = new FlagVersionService(versionRepo, new ObjectMapper().findAndRegisterModules());
            svc.record(f, FlagVersion.ChangeType.CREATE, "x");
            ArgumentCaptor<FlagVersion> cap = ArgumentCaptor.forClass(FlagVersion.class);
            verify(versionRepo).save(cap.capture());
            assertThat(cap.getValue().getVersionNumber()).isZero();

            when(versionRepo.save(any())).thenThrow(new IllegalStateException("db"));
            assertThatCode(() -> svc.record(f, FlagVersion.ChangeType.UPDATE, "x")).doesNotThrowAnyException();
        }
    }
}
