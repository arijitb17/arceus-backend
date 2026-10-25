package com.arijit.notificationservice.controller;

import com.arijit.notificationservice.dto.QueueSummaryResponse;
import com.arijit.notificationservice.entity.*;
import com.arijit.notificationservice.exception.GlobalExceptionHandler;
import com.arijit.notificationservice.repository.NotificationRecordRepository;
import com.arijit.notificationservice.scheduler.NotificationRetryScheduler;
import com.arijit.notificationservice.service.NotificationDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllersTest {

    @Mock NotificationDispatchService dispatch;
    @Mock NotificationRecordRepository repo;
    @Mock NotificationRetryScheduler retryScheduler;

    private MockMvc mvc;

    private NotificationRecord record(NotificationStatus status) {
        return NotificationRecord.builder().id(UUID.randomUUID()).channel(NotificationChannel.EMAIL).templateCode("WELCOME")
                .recipient("a@b.com").status(status).attempts(1).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(dispatch, repo))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void sendReturns201WithRecord() throws Exception {
        when(dispatch.createAndSend(eq(NotificationChannel.EMAIL), eq("WELCOME"), eq("a@b.com"), any()))
                .thenReturn(record(NotificationStatus.SENT));

        mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"template\":\"WELCOME\",\"to\":\"a@b.com\",\"variables\":{\"name\":\"Ash\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.template").value("WELCOME"))
                .andExpect(jsonPath("$.to").value("a@b.com"));
    }

    @Test
    void sendValidatesBody() throws Exception {
        mvc.perform(post("/notifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.channel").value("channel is required"))
                .andExpect(jsonPath("$.fields.template").value("template is required"))
                .andExpect(jsonPath("$.fields.to").value("to is required"));
        verifyNoInteractions(dispatch);
    }

    @Test
    void getByIdFoundAndMissing() throws Exception {
        NotificationRecord r = record(NotificationStatus.FAILED);
        when(repo.findById(r.getId())).thenReturn(Optional.of(r));
        UUID missing = UUID.randomUUID();
        when(repo.findById(missing)).thenReturn(Optional.empty());

        mvc.perform(get("/notifications/" + r.getId())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(get("/notifications/" + missing)).andExpect(status().isNotFound());
    }

    @Test
    void searchCapsPageSizeAndPassesFilters() throws Exception {
        when(repo.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record(NotificationStatus.SENT)), PageRequest.of(0, 100), 1));

        mvc.perform(get("/notifications").param("channel", "EMAIL").param("status", "SENT")
                        .param("template", "WELCOME").param("to", "a@b.com").param("size", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));

        ArgumentCaptor<Pageable> cap = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).search(eq(NotificationChannel.EMAIL), eq(NotificationStatus.SENT), eq("WELCOME"), eq("a@b.com"), cap.capture());
        assertThat(cap.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void queueListsPendingAndRetryingOnly() throws Exception {
        when(repo.findQueued(any(), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        mvc.perform(get("/notifications/queue").param("channel", "SMS")).andExpect(status().isOk());

        verify(repo).findQueued(eq(List.of(NotificationStatus.PENDING, NotificationStatus.RETRYING)),
                eq(NotificationChannel.SMS), any(Pageable.class));
    }

    @Test
    void queueSummaryCountsEachStatus() throws Exception {
        when(repo.countByStatus(NotificationStatus.PENDING)).thenReturn(1L);
        when(repo.countByStatus(NotificationStatus.RETRYING)).thenReturn(2L);
        when(repo.countByStatus(NotificationStatus.DEAD)).thenReturn(3L);
        when(repo.countByStatus(NotificationStatus.SENT)).thenReturn(4L);
        when(repo.countByStatus(NotificationStatus.FAILED)).thenReturn(5L);
        when(repo.countByStatus(NotificationStatus.SUPPRESSED)).thenReturn(6L);

        mvc.perform(get("/notifications/queue/summary"))
                .andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.retrying").value(2))
                .andExpect(jsonPath("$.dead").value(3)).andExpect(jsonPath("$.sent").value(4))
                .andExpect(jsonPath("$.failed").value(5)).andExpect(jsonPath("$.suppressed").value(6));
    }

    @Test
    void retryCallbackChecksTokenBeforeRunning() {
        RetryCallbackController controller = new RetryCallbackController(retryScheduler);
        ReflectionTestUtils.setField(controller, "internalApiToken", "tok");

        controller.retryFailed("tok");
        verify(retryScheduler).retryFailedNotifications();

        assertThatThrownBy(() -> controller.retryFailed("bad")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.retryFailed(null)).isInstanceOf(ResponseStatusException.class);
        verify(retryScheduler, times(1)).retryFailedNotifications();
    }

    @Test
    void queueSummaryRecordHoldsValues() {
        assertThat(new QueueSummaryResponse(1, 2, 3, 4, 5, 6).suppressed()).isEqualTo(6);
    }
}
