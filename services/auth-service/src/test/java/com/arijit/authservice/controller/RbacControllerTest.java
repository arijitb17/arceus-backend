package com.arijit.authservice.controller;

import com.arijit.authservice.dto.*;
import com.arijit.authservice.entity.Role;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.service.RbacService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RbacControllerTest {

    @Mock RbacService rbacService;
    @InjectMocks RbacController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mvc(controller);
    }

    private PermissionResponse perm(String code) {
        return new PermissionResponse(UUID.randomUUID(), code, "Users", "d", Instant.now(), Instant.now());
    }

    @Test
    void listPermissionsPassesFilters() throws Exception {
        when(rbacService.listPermissions("Users", 2, 5))
                .thenReturn(new PermissionPageResponse(List.of(perm("users:read")), 2, 5, 11, 3));

        mvc.perform(get("/api/auth/permissions").param("category", "Users").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].code").value("users:read"))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void getPermissionNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacService.getPermission(id)).thenThrow(new ApiException("none", HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/auth/permissions/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void createPermissionValidatesCodeFormat() throws Exception {
        mvc.perform(post("/api/auth/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"Bad Code\",\"category\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").exists());
    }

    @Test
    void createPermissionReturns201() throws Exception {
        when(rbacService.createPermission(any())).thenReturn(perm("users:write"));

        mvc.perform(post("/api/auth/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"users:write\",\"category\":\"Users\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("users:write"));
    }

    @Test
    void updateAndDeletePermission() throws Exception {
        UUID id = UUID.randomUUID();
        when(rbacService.updatePermission(any(), any())).thenReturn(perm("a:b"));

        mvc.perform(put("/api/auth/permissions/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"New\",\"description\":\"x\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/auth/permissions/" + id)).andExpect(status().isNoContent());
        verify(rbacService).deletePermission(id);
    }

    @Test
    void roleEndpointsBindEnumPathVariable() throws Exception {
        RoleDefinitionResponse role = new RoleDefinitionResponse(UUID.randomUUID(), Role.ROLE_ADMIN, "Admin", "d",
                Set.of(), Instant.now(), Instant.now());
        when(rbacService.listRoles()).thenReturn(List.of(role));
        when(rbacService.getRole(Role.ROLE_ADMIN)).thenReturn(role);
        when(rbacService.updateRoleDetails(any(), any())).thenReturn(role);
        when(rbacService.updateRolePermissions(any(), any())).thenReturn(role);

        mvc.perform(get("/api/auth/roles")).andExpect(jsonPath("$[0].roleKey").value("ROLE_ADMIN"));
        mvc.perform(get("/api/auth/roles/ROLE_ADMIN")).andExpect(status().isOk());
        mvc.perform(patch("/api/auth/roles/ROLE_ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Administrator\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/auth/roles/ROLE_ADMIN/permissions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[\"" + UUID.randomUUID() + "\"]}")).andExpect(status().isOk());
    }

    @Test
    void updateRolePermissionsRequiresIdSet() throws Exception {
        mvc.perform(put("/api/auth/roles/ROLE_USER/permissions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRoleKeyIsRejected() throws Exception {
        mvc.perform(get("/api/auth/roles/ROLE_NOPE")).andExpect(result -> {
            int s = result.getResponse().getStatus();
            if (s < 400) throw new AssertionError("expected error status but was " + s);
        });
    }
}
