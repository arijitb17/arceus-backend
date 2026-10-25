package com.arijit.schedulerservice.grpc;

import com.arijit.arceus.grpc.gateway.PingRequest;
import com.arijit.arceus.grpc.gateway.PingResponse;
import com.arijit.arceus.grpc.scheduler.*;
import com.arijit.arceus.grpc.tenant.ProvisionTenantRequest;
import com.arijit.arceus.grpc.tenant.ProvisionTenantResponse;
import com.arijit.schedulerservice.dto.CreateJobRequest;
import com.arijit.schedulerservice.entity.ExecutionTarget;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.entity.JobTriggerType;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.service.JobService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class SchedulerGrpcTest {

    @Mock JobService jobService;
    @Mock StreamObserver<ScheduleJobResponse> scheduleObs;
    @Mock StreamObserver<JobActionResponse> actionObs;
    @Mock StreamObserver<GetJobResponse> getObs;
    @Mock StreamObserver<ListJobsResponse> listObs;
    @Mock StreamObserver<PingResponse> pingObs;

    private final JobProtoMapper mapper = new JobProtoMapper();
    private SchedulerGrpcServiceImpl grpc() {
        return new SchedulerGrpcServiceImpl(jobService, mapper);
    }

    private com.arijit.schedulerservice.entity.Job entity(JobStatus status) {
        return com.arijit.schedulerservice.entity.Job.builder().id(UUID.randomUUID()).jobName("n").serviceName("svc")
                .triggerType(JobTriggerType.DELAY).executionTarget(ExecutionTarget.HTTP_CALLBACK).status(status)
                .createdAt(Instant.ofEpochMilli(1000)).build();
    }

    private <T> T captured(StreamObserver<T> obs, Class<T> type) {
        ArgumentCaptor<T> cap = ArgumentCaptor.forClass(type);
        verify(obs).onNext(cap.capture());
        verify(obs).onCompleted();
        return cap.getValue();
    }

    @Test
    void mapperEncodesOptionalFieldsAndZeroForMissingTimes() {
        var j = entity(JobStatus.SCHEDULED);
        j.setCronExpression("0 * * * * *");
        j.setLastError("boom");
        j.setNextFireAt(Instant.ofEpochMilli(5000));

        var p = mapper.toProto(j);

        assertThat(p.getId()).isEqualTo(j.getId().toString());
        assertThat(p.getTriggerType()).isEqualTo("DELAY");
        assertThat(p.getStatus()).isEqualTo("SCHEDULED");
        assertThat(p.getCronExpression()).isEqualTo("0 * * * * *");
        assertThat(p.getLastError()).isEqualTo("boom");
        assertThat(p.getCreatedAtEpochMillis()).isEqualTo(1000);
        assertThat(p.getNextFireAtEpochMillis()).isEqualTo(5000);
        assertThat(p.getStartedAtEpochMillis()).isZero();
        assertThat(p.getCallbackUrl()).isEmpty();
        assertThat(mapper.parseTriggerType("cron")).isEqualTo(JobTriggerType.CRON);
        assertThat(mapper.parseExecutionTarget("message_publish")).isEqualTo(ExecutionTarget.MESSAGE_PUBLISH);
    }

    @Test
    void scheduleJobMapsProtoDefaultsToNulls() {
        when(jobService.create(any())).thenReturn(entity(JobStatus.SCHEDULED));

        grpc().scheduleJob(ScheduleJobRequest.newBuilder().setJobName("n").setServiceName("svc").setTriggerType("delay")
                .setDelayMs(1000).setExecutionTarget("http_callback").setCallbackUrl("http://cb").build(), scheduleObs);

        ArgumentCaptor<CreateJobRequest> cap = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(jobService).create(cap.capture());
        CreateJobRequest r = cap.getValue();
        assertThat(r.triggerType()).isEqualTo(JobTriggerType.DELAY);
        assertThat(r.delayMs()).isEqualTo(1000L);
        assertThat(r.fixedRateMs()).isNull();
        assertThat(r.cronExpression()).isNull();
        assertThat(r.targetExchange()).isNull();
        assertThat(r.payload()).isNull();
        assertThat(r.maxRetries()).isNull();
        assertThat(r.retryDelayMs()).isNull();
        assertThat(r.backoffMultiplier()).isNull();
        assertThat(r.expiresAt()).isNull();
        assertThat(captured(scheduleObs, ScheduleJobResponse.class).getJob().getJobName()).isEqualTo("n");
    }

    @Test
    void scheduleJobPassesExplicitValues() {
        when(jobService.create(any())).thenReturn(entity(JobStatus.SCHEDULED));

        grpc().scheduleJob(ScheduleJobRequest.newBuilder().setJobName("n").setServiceName("s").setTriggerType("FIXED_DELAY")
                .setFixedDelayMs(500).setExecutionTarget("MESSAGE_PUBLISH").setTargetExchange("ex").setTargetRoutingKey("rk")
                .setMaxRetries(2).setRetryDelayMs(10).setBackoffMultiplier(1.5).setExpiresAtEpochMillis(9_000).setPayload("{}").build(), scheduleObs);

        ArgumentCaptor<CreateJobRequest> cap = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(jobService).create(cap.capture());
        assertThat(cap.getValue().fixedDelayMs()).isEqualTo(500L);
        assertThat(cap.getValue().maxRetries()).isEqualTo(2);
        assertThat(cap.getValue().backoffMultiplier()).isEqualTo(1.5);
        assertThat(cap.getValue().expiresAt()).isEqualTo(Instant.ofEpochMilli(9_000));
    }

    @Test
    void unknownEnumNamesRaiseIllegalArgument() {
        assertThatThrownBy(() -> grpc().scheduleJob(ScheduleJobRequest.newBuilder().setTriggerType("nope").setExecutionTarget("HTTP_CALLBACK").build(), scheduleObs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actionsReportSuccessWithJobSnapshot() {
        UUID id = UUID.randomUUID();
        when(jobService.cancel(id)).thenReturn(entity(JobStatus.CANCELLED));

        grpc().cancelJob(JobIdRequest.newBuilder().setId(id.toString()).build(), actionObs);

        JobActionResponse r = captured(actionObs, JobActionResponse.class);
        assertThat(r.getSuccess()).isTrue();
        assertThat(r.getMessage()).isEqualTo("Job cancelled");
        assertThat(r.getJob().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void actionsReportFailureForConflictsAndBadIds() {
        UUID id = UUID.randomUUID();
        when(jobService.pause(id)).thenThrow(new ApiException("cannot pause", HttpStatus.CONFLICT));

        grpc().pauseJob(JobIdRequest.newBuilder().setId(id.toString()).build(), actionObs);
        JobActionResponse conflict = captured(actionObs, JobActionResponse.class);
        assertThat(conflict.getSuccess()).isFalse();
        assertThat(conflict.getMessage()).isEqualTo("cannot pause");

        StreamObserver<JobActionResponse> bad = mock(StreamObserver.class);
        grpc().resumeJob(JobIdRequest.newBuilder().setId("not-a-uuid").build(), bad);
        JobActionResponse r = captured(bad, JobActionResponse.class);
        assertThat(r.getSuccess()).isFalse();
        assertThat(r.getMessage()).contains("not a valid job id");
    }

    @Test
    void retryJob() {
        UUID id = UUID.randomUUID();
        when(jobService.requestRetry(id)).thenReturn(entity(JobStatus.FAILED));

        grpc().retryJob(JobIdRequest.newBuilder().setId(id.toString()).build(), actionObs);

        assertThat(captured(actionObs, JobActionResponse.class).getMessage()).isEqualTo("Retry requested");
    }

    @Test
    void getJobFoundNotFoundAndInvalidId() {
        UUID id = UUID.randomUUID();
        when(jobService.getOrThrow(id)).thenReturn(entity(JobStatus.RUNNING));
        UUID missing = UUID.randomUUID();
        when(jobService.getOrThrow(missing)).thenThrow(new ApiException("nf", HttpStatus.NOT_FOUND));

        grpc().getJob(JobIdRequest.newBuilder().setId(id.toString()).build(), getObs);
        assertThat(captured(getObs, GetJobResponse.class).getFound()).isTrue();

        StreamObserver<GetJobResponse> second = mock(StreamObserver.class);
        grpc().getJob(JobIdRequest.newBuilder().setId(missing.toString()).build(), second);
        assertThat(captured(second, GetJobResponse.class).getFound()).isFalse();

        StreamObserver<GetJobResponse> third = mock(StreamObserver.class);
        grpc().getJob(JobIdRequest.newBuilder().setId("junk").build(), third);
        assertThat(captured(third, GetJobResponse.class).getFound()).isFalse();
    }

    @Test
    void listJobsClampsPagingAndParsesStatus() {
        when(jobService.list(any(), any(), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(entity(JobStatus.FAILED)), PageRequest.of(0, 100), 1));

        grpc().listJobs(ListJobsRequest.newBuilder().setServiceName("svc").setStatus("failed").setPage(-3).setSize(9999).build(), listObs);

        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(jobService).list(eq("svc"), eq(JobStatus.FAILED), pg.capture());
        assertThat(pg.getValue().getPageNumber()).isZero();
        assertThat(pg.getValue().getPageSize()).isEqualTo(100);
        ListJobsResponse r = captured(listObs, ListJobsResponse.class);
        assertThat(r.getJobsList()).hasSize(1);
        assertThat(r.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listJobsDefaultsSizeToTwentyAndNullFilters() {
        when(jobService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        grpc().listJobs(ListJobsRequest.getDefaultInstance(), listObs);

        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(jobService).list(isNull(), isNull(), pg.capture());
        assertThat(pg.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void pingAndTenantProvisioning() {
        grpc().ping(PingRequest.getDefaultInstance(), pingObs);
        assertThat(captured(pingObs, PingResponse.class).getService()).isEqualTo("scheduler-service");

        StreamObserver<ProvisionTenantResponse> obs = mock(StreamObserver.class);
        new SchedulerTenantProvisioningGrpcService().provisionTenant(ProvisionTenantRequest.getDefaultInstance(), obs);
        assertThat(captured(obs, ProvisionTenantResponse.class).getSuccess()).isTrue();
    }
}
