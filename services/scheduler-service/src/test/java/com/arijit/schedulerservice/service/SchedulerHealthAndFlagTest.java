package com.arijit.schedulerservice.service;

import com.arijit.arceus.grpc.featureflag.EvaluateFlagRequest;
import com.arijit.arceus.grpc.featureflag.EvaluateFlagResponse;
import com.arijit.arceus.grpc.featureflag.FeatureFlagGrpcServiceGrpc;
import com.arijit.schedulerservice.dto.HealthResponse;
import com.arijit.schedulerservice.entity.JobStatus;
import com.arijit.schedulerservice.featureflag.JobFlagGateService;
import com.arijit.schedulerservice.repository.JobRepository;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerHealthAndFlagTest {

    @Mock JobRepository jobRepo;
    @Mock ThreadPoolTaskExecutor executor;
    @Mock ConnectionFactory rabbitFactory;
    @Mock Connection connection;
    @Mock FeatureFlagGrpcServiceGrpc.FeatureFlagGrpcServiceBlockingStub stub;

    private void stubPool() {
        ThreadPoolExecutor tpe = mock(ThreadPoolExecutor.class);
        when(tpe.getQueue()).thenReturn(new LinkedBlockingQueue<>());
        when(executor.getThreadPoolExecutor()).thenReturn(tpe);
        when(executor.getPoolSize()).thenReturn(2);
        when(executor.getActiveCount()).thenReturn(1);
    }

    @Test
    void healthyWhenDbAndRabbitUp() {
        stubPool();
        when(rabbitFactory.createConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        when(jobRepo.countByStatus(JobStatus.RUNNING)).thenReturn(3L);

        HealthResponse r = new HealthService(jobRepo, executor, rabbitFactory).check();

        assertThat(r.status()).isEqualTo("UP");
        assertThat(r.activeJobs()).isEqualTo(3);
        assertThat(r.workerPoolSize()).isEqualTo(2);
        assertThat(r.rabbitMqStatus()).isEqualTo("UP");
        assertThat(r.databaseStatus()).isEqualTo("UP");
        verify(connection).close();
    }

    @Test
    void degradedWhenDatabaseOrRabbitDown() {
        stubPool();
        when(jobRepo.count()).thenThrow(new IllegalStateException("db"));
        when(rabbitFactory.createConnection()).thenThrow(new IllegalStateException("amqp"));

        HealthResponse r = new HealthService(jobRepo, executor, rabbitFactory).check();

        assertThat(r.status()).isEqualTo("DEGRADED");
        assertThat(r.databaseStatus()).isEqualTo("DOWN");
        assertThat(r.rabbitMqStatus()).isEqualTo("DOWN");
    }

    @Test
    void closedRabbitConnectionIsDown() {
        stubPool();
        when(rabbitFactory.createConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(false);

        assertThat(new HealthService(jobRepo, executor, rabbitFactory).check().rabbitMqStatus()).isEqualTo("DOWN");
    }

    private JobFlagGateService gate() {
        JobFlagGateService g = new JobFlagGateService(stub);
        ReflectionTestUtils.setField(g, "environment", "PRODUCTION");
        return g;
    }

    @Test
    void flagGateAllowsUnknownEnabledAndFailsOpen() {
        when(stub.evaluateFlag(any())).thenReturn(
                EvaluateFlagResponse.newBuilder().setFound(false).build(),
                EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(true).build(),
                EvaluateFlagResponse.newBuilder().setFound(true).setEnabled(false).build())
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        JobFlagGateService gate = gate();

        assertThat(gate.isExecutionEnabledFor("auth-service")).isTrue();
        assertThat(gate.isExecutionEnabledFor("auth-service")).isTrue();
        assertThat(gate.isExecutionEnabledFor("auth-service")).isFalse();
        assertThat(gate.isExecutionEnabledFor("auth-service")).isTrue();
    }

    @Test
    void flagKeyPerOwnerService() {
        when(stub.evaluateFlag(any())).thenReturn(EvaluateFlagResponse.getDefaultInstance());

        gate().isExecutionEnabledFor("cache-service");

        ArgumentCaptor<EvaluateFlagRequest> cap = ArgumentCaptor.forClass(EvaluateFlagRequest.class);
        verify(stub).evaluateFlag(cap.capture());
        assertThat(cap.getValue().getKey()).isEqualTo("scheduler.jobs.cache-service.enabled");
        assertThat(cap.getValue().getBucketKey()).isEqualTo("cache-service");
        assertThat(cap.getValue().getEnvironment()).isEqualTo("PRODUCTION");
    }
}
