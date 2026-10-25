package com.arijit.notificationservice.config;

import com.arijit.arceus.grpc.scheduler.*;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.entity.NotificationTemplate;
import com.arijit.notificationservice.repository.NotificationTemplateRepository;
import com.arijit.notificationservice.scheduler.RetryJobRegistrar;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateSeederAndRegistrarTest {

    @Mock NotificationTemplateRepository templates;
    @Mock SchedulerGrpcServiceGrpc.SchedulerGrpcServiceBlockingStub stub;

    @Test
    void seedsAllFiveDefaultTemplatesWhenEmpty() {
        when(templates.existsByChannelAndCode(any(), anyString())).thenReturn(false);

        new TemplateSeeder(templates).run();

        ArgumentCaptor<NotificationTemplate> cap = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templates, times(5)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(NotificationTemplate::getCode)
                .containsExactly("WELCOME", "DEPLOYMENT_SUCCESS", "DEPLOYMENT_FAILURE", "DEPLOYMENT_ROLLED_BACK", "DEPLOYMENT_CANCELLED");
        assertThat(cap.getAllValues()).allSatisfy(t -> {
            assertThat(t.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(t.isActive()).isTrue();
            assertThat(t.getBody()).contains("{{");
        });
    }

    @Test
    void seedingIsIdempotent() {
        when(templates.existsByChannelAndCode(any(), anyString())).thenReturn(true);

        new TemplateSeeder(templates).run();

        verify(templates, never()).save(any());
    }

    @Test
    void onlyMissingTemplatesAreSeeded() {
        when(templates.existsByChannelAndCode(any(), anyString())).thenReturn(true);
        when(templates.existsByChannelAndCode(NotificationChannel.EMAIL, "WELCOME")).thenReturn(false);

        new TemplateSeeder(templates).run();

        ArgumentCaptor<NotificationTemplate> cap = ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(templates).save(cap.capture());
        assertThat(cap.getValue().getCode()).isEqualTo("WELCOME");
    }

    private RetryJobRegistrar registrar() {
        RetryJobRegistrar r = new RetryJobRegistrar(stub);
        ReflectionTestUtils.setField(r, "retryBackoffSeconds", 45);
        ReflectionTestUtils.setField(r, "callbackBaseUrl", "http://notif:8083");
        ReflectionTestUtils.setField(r, "internalApiToken", "tok");
        return r;
    }

    @Test
    void registrarSchedulesFixedDelayCallback() {
        when(stub.listJobs(any())).thenReturn(ListJobsResponse.getDefaultInstance());
        when(stub.scheduleJob(any())).thenReturn(ScheduleJobResponse.newBuilder().setJob(Job.newBuilder().setId("j")).build());

        registrar().run(new DefaultApplicationArguments());

        ArgumentCaptor<ScheduleJobRequest> cap = ArgumentCaptor.forClass(ScheduleJobRequest.class);
        verify(stub).scheduleJob(cap.capture());
        assertThat(cap.getValue().getJobName()).isEqualTo("notification-retry");
        assertThat(cap.getValue().getFixedDelayMs()).isEqualTo(45_000L);
        assertThat(cap.getValue().getCallbackUrl()).isEqualTo("http://notif:8083/internal/notifications/retry-failed?token=tok");
        assertThat(cap.getValue().getTriggerType()).isEqualTo("FIXED_DELAY");
    }

    @Test
    void registrarSkipsWhenActiveJobExists() {
        when(stub.listJobs(any())).thenReturn(ListJobsResponse.newBuilder()
                .addJobs(Job.newBuilder().setJobName("notification-retry").setStatus("SCHEDULED")).build());

        registrar().run(new DefaultApplicationArguments());

        verify(stub, never()).scheduleJob(any());
    }

    @Test
    void registrarReschedulesWhenOnlyCancelledJobExistsAndSurvivesOutage() {
        when(stub.listJobs(any())).thenReturn(ListJobsResponse.newBuilder()
                .addJobs(Job.newBuilder().setJobName("notification-retry").setStatus("CANCELLED")).build());
        when(stub.scheduleJob(any())).thenReturn(ScheduleJobResponse.getDefaultInstance());
        registrar().run(new DefaultApplicationArguments());
        verify(stub).scheduleJob(any());

        when(stub.listJobs(any())).thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));
        assertThatCode(() -> registrar().run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }
}
