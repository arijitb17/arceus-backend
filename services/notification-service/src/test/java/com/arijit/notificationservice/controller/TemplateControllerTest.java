package com.arijit.notificationservice.controller;

import com.arijit.notificationservice.dto.*;
import com.arijit.notificationservice.entity.NotificationChannel;
import com.arijit.notificationservice.exception.ApiException;
import com.arijit.notificationservice.exception.GlobalExceptionHandler;
import com.arijit.notificationservice.service.TemplateManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {

    @Mock TemplateManagementService service;
    private MockMvc mvc;
    private final UUID id = UUID.randomUUID();

    private TemplateResponse tpl(boolean active) {
        return new TemplateResponse(id, NotificationChannel.EMAIL, "WELCOME", "s", "b", active);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TemplateController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listDelegatesWithFilters() throws Exception {
        when(service.list(NotificationChannel.EMAIL, 1, 5)).thenReturn(new PagedResponse<>(List.of(tpl(true)), 1, 5, 6, 2));

        mvc.perform(get("/notifications/templates").param("channel", "EMAIL").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("WELCOME"))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void getAndNotFound() throws Exception {
        when(service.get(id)).thenReturn(tpl(true));
        UUID other = UUID.randomUUID();
        when(service.get(other)).thenThrow(new ApiException("nf", HttpStatus.NOT_FOUND));

        mvc.perform(get("/notifications/templates/" + id)).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/notifications/templates/" + other)).andExpect(status().isNotFound());
    }

    @Test
    void createValidatesAndReturns201() throws Exception {
        when(service.create(any())).thenReturn(tpl(true));

        mvc.perform(post("/notifications/templates").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").value("code is required"));
        mvc.perform(post("/notifications/templates").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"code\":\"WELCOME\",\"body\":\"b\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void updateStatusAndDelete() throws Exception {
        when(service.update(any(), any())).thenReturn(tpl(true));
        when(service.updateStatus(any(), any())).thenReturn(tpl(false));

        mvc.perform(put("/notifications/templates/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"s\",\"body\":\"b\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/notifications/templates/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"subject\":\"s\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/notifications/templates/" + id + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(delete("/notifications/templates/" + id)).andExpect(status().isNoContent());
        verify(service).delete(id);
    }
}
