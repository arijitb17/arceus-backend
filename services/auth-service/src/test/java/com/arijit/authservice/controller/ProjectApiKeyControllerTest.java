package com.arijit.authservice.controller;

import com.arijit.authservice.entity.ApiKey;
import com.arijit.authservice.entity.ApiKeyType;
import com.arijit.authservice.exception.ApiException;
import com.arijit.authservice.service.ApiKeyService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProjectApiKeyControllerTest {

    @Mock ApiKeyService apiKeyService;
    @InjectMocks ProjectApiKeyController controller;
    private MockMvc mvc;
    private final UUID org = UUID.randomUUID();
    private final UUID project = UUID.randomUUID();
    private String base;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mvc(controller);
        ControllerTestSupport.authenticateAs("admin@org.io");
        base = "/api/auth/organizations/" + org + "/projects/" + project + "/api-keys";
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clear();
    }

    @Test
    void createReturnsRawKeyAndMetadata() throws Exception {
        UUID id = UUID.randomUUID();
        when(apiKeyService.createProjectKey("admin@org.io", org, project, "prod", ApiKeyType.SECRET, "backend", "read"))
                .thenReturn("sk_raw");
        when(apiKeyService.listForProject("admin@org.io", org, project, "prod")).thenReturn(List.of(
                ApiKey.builder().id(id).name("backend").keyType(ApiKeyType.SECRET).environment("prod").build()));

        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"backend\",\"keyType\":\"SECRET\",\"environment\":\"prod\",\"scopes\":\"read\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.keyType").value("SECRET"))
                .andExpect(jsonPath("$.environment").value("prod"))
                .andExpect(jsonPath("$.key").value("sk_raw"));
    }

    @Test
    void createRequiresKeyTypeAndEnvironment() throws Exception {
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.keyType").exists())
                .andExpect(jsonPath("$.fields.environment").exists());
    }

    @Test
    void createForbiddenWhenNotManager() throws Exception {
        when(apiKeyService.createProjectKey(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new ApiException("Requires OWNER or ADMIN", HttpStatus.FORBIDDEN));

        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"keyType\":\"PUBLIC\",\"environment\":\"dev\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRequiresEnvironmentParamAndMasksKeys() throws Exception {
        when(apiKeyService.listForProject("admin@org.io", org, project, "dev")).thenReturn(List.of(
                ApiKey.builder().id(UUID.randomUUID()).name("n").keyPrefix("pk_12345").keyType(ApiKeyType.PUBLIC).environment("dev").build()));

        mvc.perform(get(base).param("environment", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyType").value("PUBLIC"))
                .andExpect(jsonPath("$[0].keyHash").doesNotExist());

        mvc.perform(get(base)).andExpect(status().isBadRequest());
    }

    @Test
    void revealAndRevoke() throws Exception {
        UUID id = UUID.randomUUID();
        when(apiKeyService.revealProjectKey("admin@org.io", org, project, id)).thenReturn("sk_plain");

        mvc.perform(get(base + "/" + id + "/reveal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("sk_plain"));

        mvc.perform(delete(base + "/" + id)).andExpect(status().isNoContent());
        verify(apiKeyService).revokeProjectKey("admin@org.io", org, project, id);
    }
}
