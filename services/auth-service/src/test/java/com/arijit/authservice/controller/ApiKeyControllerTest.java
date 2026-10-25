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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    @Mock ApiKeyService apiKeyService;
    @InjectMocks ApiKeyController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = ControllerTestSupport.mvc(controller);
        ControllerTestSupport.authenticateAs("ash@pallet.town");
    }

    @AfterEach
    void tearDown() {
        ControllerTestSupport.clear();
    }

    @Test
    void createReturnsRawKeyOnceWithNewestKeyId() throws Exception {
        UUID id = UUID.randomUUID();
        when(apiKeyService.create("ash@pallet.town", "ci")).thenReturn("ak_raw");
        when(apiKeyService.listForOwner("ash@pallet.town")).thenReturn(List.of(ApiKey.builder().id(id).name("ci").build()));

        mvc.perform(post("/api/auth/api-keys").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ci\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("ci"))
                .andExpect(jsonPath("$.key").value("ak_raw"));
    }

    @Test
    void createValidatesName() throws Exception {
        mvc.perform(post("/api/auth/api-keys").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/api-keys").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listMasksKeyWithPrefixOnly() throws Exception {
        when(apiKeyService.listForOwner("ash@pallet.town")).thenReturn(List.of(
                ApiKey.builder().id(UUID.randomUUID()).name("k").keyPrefix("ak_abcde").keyHash("HASH")
                        .encryptedKey("ENC").createdAt(Instant.now()).build()));

        mvc.perform(get("/api/auth/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].keyPrefix").value("ak_abcde..."))
                .andExpect(jsonPath("$[0].keyHash").doesNotExist())
                .andExpect(jsonPath("$[0].encryptedKey").doesNotExist());
    }

    @Test
    void revealReturnsKey() throws Exception {
        UUID id = UUID.randomUUID();
        when(apiKeyService.revealForOwner("ash@pallet.town", id)).thenReturn("ak_plain");

        mvc.perform(get("/api/auth/api-keys/" + id + "/reveal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("ak_plain"));
    }

    @Test
    void revealLegacyKeyReturns410() throws Exception {
        UUID id = UUID.randomUUID();
        when(apiKeyService.revealForOwner(any(), any())).thenThrow(new ApiException("gone", HttpStatus.GONE));

        mvc.perform(get("/api/auth/api-keys/" + id + "/reveal")).andExpect(status().isGone());
    }

    @Test
    void revokeReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/auth/api-keys/" + id)).andExpect(status().isNoContent());

        verify(apiKeyService).revoke("ash@pallet.town", id);
    }

    @Test
    void projectKeyDtoMappingHandlesNullType() {
        var r = com.arijit.authservice.dto.ProjectApiKeyResponse.from(
                ApiKey.builder().id(UUID.randomUUID()).keyType(null).keyPrefix("pk_1").build());
        org.assertj.core.api.Assertions.assertThat(r.keyType()).isNull();
        var r2 = com.arijit.authservice.dto.ProjectApiKeyResponse.from(
                ApiKey.builder().id(UUID.randomUUID()).keyType(ApiKeyType.PUBLIC).build());
        org.assertj.core.api.Assertions.assertThat(r2.keyType()).isEqualTo("PUBLIC");
    }
}
