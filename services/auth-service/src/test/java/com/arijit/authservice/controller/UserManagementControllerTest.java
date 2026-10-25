package com.arijit.authservice.controller;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.AuthProvider;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.service.UserManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {

    @Mock UserManagementService service;
    @InjectMocks UserManagementController controller;
    private MockMvc mvc;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mvc(controller);
        ControllerTestSupport.authenticateAs("admin@arceus.io");
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clear();
    }

    private AdminUserResponse user(boolean enabled) {
        return new AdminUserResponse(id, "A", "a@b.c", "+911", true, true, enabled, AuthProvider.LOCAL,
                Set.of(Role.ROLE_USER), Instant.now(), Instant.now());
    }

    @Test
    void listUsersWithQueryAndPaging() throws Exception {
        when(service.listUsers("ash", 1, 10)).thenReturn(new UserPageResponse(List.of(user(true)), 1, 10, 11, 2));

        mvc.perform(get("/api/auth/users").param("query", "ash").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("a@b.c"))
                .andExpect(jsonPath("$.totalElements").value(11));
    }

    @Test
    void getUserNotFound() throws Exception {
        when(service.getUser(id)).thenThrow(new ApiException("no", HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/auth/users/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void updateRolesPassesActingAdmin() throws Exception {
        when(service.updateRoles(eq(id), any(), eq("admin@arceus.io"))).thenReturn(user(true));

        mvc.perform(patch("/api/auth/users/" + id + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_ADMIN\"]}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateRolesRejectsEmptySet() throws Exception {
        mvc.perform(patch("/api/auth/users/" + id + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.roles").value("At least one role is required"));
    }

    @Test
    void updateRolesSelfDemotionSurfacesAs400() throws Exception {
        when(service.updateRoles(any(), any(), any()))
                .thenThrow(new ApiException("You can't remove your own admin role", HttpStatus.BAD_REQUEST));

        mvc.perform(patch("/api/auth/users/" + id + "/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"ROLE_USER\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You can't remove your own admin role"));
    }

    @Test
    void updateStatus() throws Exception {
        when(service.updateStatus(eq(id), any(), eq("admin@arceus.io"))).thenReturn(user(false));

        mvc.perform(patch("/api/auth/users/" + id + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(false));
    }

    @Test
    void sessionEndpoints() throws Exception {
        UUID sid = UUID.randomUUID();
        when(service.listSessions(id, 0, 20)).thenReturn(new SessionPageResponse(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/auth/users/" + id + "/sessions")).andExpect(status().isOk());
        mvc.perform(delete("/api/auth/users/" + id + "/sessions/" + sid)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/auth/users/" + id + "/sessions")).andExpect(status().isNoContent());

        verify(service).revokeSession(id, sid);
        verify(service).revokeAllSessions(id);
    }
}
