package com.arijit.cacheservice.scheduler;

import com.arijit.arceus.grpc.scheduler.*;
import com.arijit.cacheservice.CacheTestFixtures;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SweepJobRegistrarTest {

    @Mock SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub stub;
    private SweepJobRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new SweepJobRegistrar(stub, CacheTestFixtures.properties());
    }

    private ListJobsResponse jobs(Job... jobs) {
        return ListJobsResponse.newBuilder().addAllJobs(java.util.List.of(jobs)).build();
    }

    @Test
    void registersFixedDelayCallbackJobWhenNoneExists() {
        when(stub.listJobs(any())).thenReturn(jobs());
        when(stub.scheduleJob(any())).thenReturn(ScheduleJobResponse.newBuilder().setJob(Job.newBuilder().setId("j1")).build());

        registrar.run(new DefaultApplicationArguments());

        ArgumentCaptor<ScheduleJobRequest> cap = ArgumentCaptor.forClass(ScheduleJobRequest.class);
        verify(stub).scheduleJob(cap.capture());
        ScheduleJobRequest r = cap.getValue();
        assertThat(r.getJobName()).isEqualTo("cache-sweep");
        assertThat(r.getServiceName()).isEqualTo("cache-service");
        assertThat(r.getTriggerType()).isEqualTo("FIXED_DELAY");
        assertThat(r.getFixedDelayMs()).isEqualTo(60_000L);
        assertThat(r.getExecutionTarget()).isEqualTo("HTTP_CALLBACK");
        assertThat(r.getCallbackUrl()).isEqualTo("http://localhost:8085/internal/cache/sweep?token=internal-token");
        assertThat(r.getMaxRetries()).isZero();
    }

    @Test
    void skipsWhenActiveJobAlreadyRegistered() {
        when(stub.listJobs(any())).thenReturn(jobs(Job.newBuilder().setJobName("cache-sweep").setStatus("SCHEDULED").build()));

        registrar.run(new DefaultApplicationArguments());

        verify(stub, never()).scheduleJob(any());
    }

    @Test
    void cancelledJobDoesNotCountAsRegistered() {
        when(stub.listJobs(any())).thenReturn(jobs(Job.newBuilder().setJobName("cache-sweep").setStatus("CANCELLED").build()));
        when(stub.scheduleJob(any())).thenReturn(ScheduleJobResponse.getDefaultInstance());

        registrar.run(new DefaultApplicationArguments());

        verify(stub).scheduleJob(any());
    }

    @Test
    void schedulerOutageIsLoggedNotThrown() {
        when(stub.listJobs(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        assertThatCode(() -> registrar.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
