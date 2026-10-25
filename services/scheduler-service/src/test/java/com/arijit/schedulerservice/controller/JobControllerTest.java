package com.arijit.schedulerservice.controller;

import com.arijit.schedulerservice.dto.JobStatsResponse;
import com.arijit.schedulerservice.entity.*;
import com.arijit.schedulerservice.exception.ApiException;
import com.arijit.schedulerservice.exception.GlobalExceptionHandler;
import com.arijit.schedulerservice.repository.JobExecutionHistoryRepository;
import com.arijit.schedulerservice.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock JobService jobService;
    @Mock JobExecutionHistoryRepository historyRepo;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new JobController(jobService, historyRepo))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private Job job(JobStatus status) {
        return Job.builder().id(UUID.randomUUID()).jobName("nightly").serviceName("svc").triggerType(JobTriggerType.CRON)
                .executionTarget(ExecutionTarget.HTTP_CALLBACK).status(status).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    private static final String CREATE_JSON = "{\"jobName\":\"nightly\",\"serviceName\":\"svc\",\"triggerType\":\"DELAY\","
            + "\"delayMs\":1000,\"executionTarget\":\"HTTP_CALLBACK\",\"callbackUrl\":\"http://x\"}";

    @Test
    void createReturns201() throws Exception {
        Job j = job(JobStatus.SCHEDULED);
        when(jobService.create(any())).thenReturn(j);

        mvc.perform(post("/jobs").contentType(MediaType.APPLICATION_JSON).content(CREATE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(j.getId().toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    void createValidatesRequiredFields() throws Exception {
        mvc.perform(post("/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
        verifyNoInteractions(jobService);
    }

    @Test
    void getByIdAndNotFound() throws Exception {
        Job j = job(JobStatus.RUNNING);
        when(jobService.getOrThrow(j.getId())).thenReturn(j);
        UUID missing = UUID.randomUUID();
        when(jobService.getOrThrow(missing)).thenThrow(new ApiException("no job", HttpStatus.NOT_FOUND));

        mvc.perform(get("/jobs/" + j.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RUNNING"));
        mvc.perform(get("/jobs/" + missing)).andExpect(status().isNotFound());
    }

    @Test
    void listCapsSizeAndSortsNewestFirst() throws Exception {
        when(jobService.list(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(job(JobStatus.SCHEDULED)), PageRequest.of(1, 100), 101));

        mvc.perform(get("/jobs").param("serviceName", "svc").param("status", "SCHEDULED").param("page", "1").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(101));

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(jobService).list(org.mockito.ArgumentMatchers.eq("svc"), org.mockito.ArgumentMatchers.eq(JobStatus.SCHEDULED), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
        assertThat(cap.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }

    @Test
    void historyChecksJobExistsThenPages() throws Exception {
        UUID id = UUID.randomUUID();
        JobExecutionHistory h = JobExecutionHistory.builder().id(UUID.randomUUID()).jobId(id).jobName("nightly")
                .attemptNumber(2).resultStatus(JobStatus.FAILED).startedAt(Instant.now()).build();
        when(historyRepo.findByJobIdOrderByStartedAtDesc(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(h)));

        mvc.perform(get("/jobs/" + id + "/history"))
                .andExpect(jsonPath("$.items[0].attemptNumber").value(2))
                .andExpect(jsonPath("$.items[0].resultStatus").value("FAILED"));
        verify(jobService).getOrThrow(id);
    }

    @Test
    void updateDelegates() throws Exception {
        Job j = job(JobStatus.SCHEDULED);
        when(jobService.update(any(), any())).thenReturn(j);

        mvc.perform(put("/jobs/" + j.getId()).contentType(MediaType.APPLICATION_JSON).content("{\"priority\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCancelsActiveJobsButIsIdempotentForTerminalOnes() throws Exception {
        Job active = job(JobStatus.SCHEDULED);
        Job done = job(JobStatus.COMPLETED);
        when(jobService.getOrThrow(active.getId())).thenReturn(active);
        when(jobService.getOrThrow(done.getId())).thenReturn(done);
        when(jobService.cancel(active.getId())).thenReturn(job(JobStatus.CANCELLED));

        mvc.perform(delete("/jobs/" + active.getId())).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/jobs/" + done.getId())).andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(jobService, never()).cancel(done.getId());
    }

    @Test
    void lifecycleActions() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.pause(id)).thenReturn(job(JobStatus.PAUSED));
        when(jobService.resume(id)).thenReturn(job(JobStatus.SCHEDULED));
        when(jobService.cancel(id)).thenReturn(job(JobStatus.CANCELLED));
        when(jobService.requestRetry(id)).thenReturn(job(JobStatus.FAILED));

        mvc.perform(post("/jobs/" + id + "/pause")).andExpect(jsonPath("$.status").value("PAUSED"));
        mvc.perform(post("/jobs/" + id + "/resume")).andExpect(jsonPath("$.status").value("SCHEDULED"));
        mvc.perform(post("/jobs/" + id + "/cancel")).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/jobs/" + id + "/retry")).andExpect(status().isAccepted());
    }

    @Test
    void conflictsAndOptimisticLockMapTo409() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.pause(id)).thenThrow(new ApiException("not pausable", HttpStatus.CONFLICT));
        when(jobService.resume(id)).thenThrow(new OptimisticLockingFailureException("stale"));

        mvc.perform(post("/jobs/" + id + "/pause")).andExpect(status().isConflict());
        mvc.perform(post("/jobs/" + id + "/resume")).andExpect(status().isConflict());
    }

    @Test
    void statsEndpoint() throws Exception {
        when(jobService.stats()).thenReturn(new JobStatsResponse(Map.of("FAILED", 2L), 2, 4, 1, 0, 1));

        mvc.perform(get("/jobs/stats"))
                .andExpect(jsonPath("$.totalJobs").value(2))
                .andExpect(jsonPath("$.countsByStatus.FAILED").value(2))
                .andExpect(jsonPath("$.deadLetterCount").value(1));
    }
}
