package com.arijit.featureflagservice.controller;

import com.arijit.featureflagservice.FlagTestData;
import com.arijit.featureflagservice.entity.*;
import com.arijit.featureflagservice.exception.ApiException;
import com.arijit.featureflagservice.exception.GlobalExceptionHandler;
import com.arijit.featureflagservice.repository.FlagAuditLogRepository;
import com.arijit.featureflagservice.service.FlagService;
import com.arijit.featureflagservice.service.FlagValueCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FlagControllersTest {

    @Mock FlagService flagService;
    @Mock FlagAuditLogRepository auditRepo;

    private MockMvc mvc;
    private final FeatureFlag flag = FlagTestData.booleanFlag("checkout", Environment.GLOBAL, FlagStatus.ENABLED, true);

    @BeforeEach
    void setUp() {
        FlagController controller = new FlagController(flagService, new FlagValueCodec(new ObjectMapper()), auditRepo);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createReturns201AndDecodedValue() throws Exception {
        when(flagService.create(any())).thenReturn(flag);

        mvc.perform(post("/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"checkout\",\"name\":\"Checkout\",\"type\":\"BOOLEAN\",\"environment\":\"GLOBAL\",\"value\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value("checkout"))
                .andExpect(jsonPath("$.value").value(true))
                .andExpect(jsonPath("$.status").value("ENABLED"));
    }

    @Test
    void createValidatesRequiredFields() throws Exception {
        mvc.perform(post("/flags").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
        verifyNoInteractions(flagService);
    }

    @Test
    void createConflictMapped() throws Exception {
        when(flagService.create(any())).thenThrow(new ApiException("exists", HttpStatus.CONFLICT));

        mvc.perform(post("/flags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"checkout\",\"name\":\"C\",\"type\":\"BOOLEAN\",\"value\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listIsPagedAndFiltered() throws Exception {
        when(flagService.list(eq(Environment.STAGING), eq(FlagStatus.ENABLED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(flag), PageRequest.of(1, 5), 11));

        mvc.perform(get("/flags").param("environment", "STAGING").param("status", "ENABLED")
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].key").value("checkout"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void getDefaultsToGlobalEnvironment() throws Exception {
        when(flagService.getOrThrow("checkout", Environment.GLOBAL)).thenReturn(flag);

        mvc.perform(get("/flags/checkout")).andExpect(status().isOk()).andExpect(jsonPath("$.key").value("checkout"));
    }

    @Test
    void getUnknownIs404() throws Exception {
        when(flagService.getOrThrow(any(), any())).thenThrow(new ApiException("nope", HttpStatus.NOT_FOUND));

        mvc.perform(get("/flags/missing")).andExpect(status().isNotFound());
    }

    @Test
    void updateDeleteEnableDisable() throws Exception {
        when(flagService.update(eq("checkout"), eq(Environment.PRODUCTION), any())).thenReturn(flag);
        when(flagService.enable("checkout", Environment.GLOBAL, "me")).thenReturn(flag);
        when(flagService.disable("checkout", Environment.GLOBAL, null)).thenReturn(flag);

        mvc.perform(put("/flags/checkout").param("environment", "PRODUCTION").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}")).andExpect(status().isOk());
        mvc.perform(delete("/flags/checkout").param("actor", "me")).andExpect(status().isNoContent());
        mvc.perform(post("/flags/checkout/enable").param("actor", "me")).andExpect(status().isOk());
        mvc.perform(post("/flags/checkout/disable")).andExpect(status().isOk());

        verify(flagService).delete("checkout", Environment.GLOBAL, "me");
    }

    @Test
    void historyUsesKeyFilterWhenProvided() throws Exception {
        FlagAuditLog log = FlagAuditLog.builder().flagKey("checkout").action("UPDATE").actor("a")
                .timestamp(Instant.now()).build();
        when(auditRepo.findByFlagKeyOrderByTimestampDesc(eq("checkout"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(auditRepo.findAllByOrderByTimestampDesc(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(log, log)));

        mvc.perform(get("/flags/history").param("key", "checkout"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("UPDATE"));
        mvc.perform(get("/flags/history").param("key", "  ")).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/flags/history")).andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void scheduleCallbacksRequireValidToken() {
        var callback = new FlagScheduleCallbackController(flagService, FlagTestData.properties(true, 60, true));

        callback.scheduledActivate("checkout", Environment.STAGING, "internal-token");
        callback.scheduledDeactivate("checkout", Environment.STAGING, "internal-token");

        verify(flagService).applyScheduledActivation("checkout", Environment.STAGING);
        verify(flagService).applyScheduledDeactivation("checkout", Environment.STAGING);

        assertThatThrownBy(() -> callback.scheduledActivate("checkout", Environment.STAGING, "bad"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> callback.scheduledDeactivate("checkout", Environment.STAGING, null))
                .isInstanceOf(ApiException.class);
        verifyNoMoreInteractions(flagService);
    }
}
