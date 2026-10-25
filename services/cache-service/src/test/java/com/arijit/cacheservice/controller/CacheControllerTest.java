package com.arijit.cacheservice.controller;

import com.arijit.cacheservice.dto.*;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.exception.GlobalExceptionHandler;
import com.arijit.cacheservice.service.CacheEvictionService;
import com.arijit.cacheservice.service.CacheStatsService;
import com.arijit.cacheservice.service.CacheStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CacheControllerTest {

    @Mock CacheStoreService store;
    @Mock CacheEvictionService eviction;
    @Mock CacheStatsService stats;
    @InjectMocks CacheController controller;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private CacheResponse response(Object value) {
        return new CacheResponse("ns", "k", value, CacheDataType.STRING, SerializationFormat.JSON,
                TtlPolicy.NO_EXPIRY, -1, Instant.now(), Instant.now());
    }

    @Test
    void setReturns201WithBody() throws Exception {
        when(store.set(any())).thenReturn(response(Map.of("a", 1)));

        mvc.perform(post("/cache").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"ns\",\"key\":\"k\",\"value\":{\"a\":1}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.namespace").value("ns"))
                .andExpect(jsonPath("$.value.a").value(1));

        ArgumentCaptor<CacheRequest> cap = ArgumentCaptor.forClass(CacheRequest.class);
        verify(store).set(cap.capture());
        assertThat(cap.getValue().key()).isEqualTo("k");
    }

    @Test
    void serviceErrorsMapToTheirStatus() throws Exception {
        when(store.set(any())).thenThrow(new ApiException("too big", HttpStatus.PAYLOAD_TOO_LARGE));

        mvc.perform(post("/cache").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"ns\",\"key\":\"k\",\"value\":\"x\"}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("too big"));
    }

    @Test
    void getHitAndMiss() throws Exception {
        when(store.get("ns", "k")).thenReturn(Optional.of(response("v")));
        when(store.get("ns", "gone")).thenReturn(Optional.empty());

        mvc.perform(get("/cache/ns/k")).andExpect(status().isOk()).andExpect(jsonPath("$.value").value("v"));
        mvc.perform(get("/cache/ns/gone")).andExpect(status().isNotFound());
    }

    @Test
    void updateOverridesNamespaceAndKeyFromPath() throws Exception {
        when(store.update(any())).thenReturn(response("v"));

        mvc.perform(put("/cache/pathns/pathkey").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"namespace\":\"bodyns\",\"key\":\"bodykey\",\"value\":\"v\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CacheRequest> cap = ArgumentCaptor.forClass(CacheRequest.class);
        verify(store).update(cap.capture());
        assertThat(cap.getValue().namespace()).isEqualTo("pathns");
        assertThat(cap.getValue().key()).isEqualTo("pathkey");
    }

    @Test
    void deleteReturns204OrNotFound() throws Exception {
        when(store.delete("ns", "k")).thenReturn(true);
        when(store.delete("ns", "gone")).thenReturn(false);

        mvc.perform(delete("/cache/ns/k")).andExpect(status().isNoContent());
        mvc.perform(delete("/cache/ns/gone")).andExpect(status().isNotFound());
    }

    @Test
    void existsAndTtl() throws Exception {
        when(store.exists("ns", "k")).thenReturn(true);
        when(store.ttl("ns", "k")).thenReturn(30L);

        mvc.perform(get("/cache/ns/k/exists")).andExpect(jsonPath("$.exists").value(true));
        mvc.perform(get("/cache/ns/k/ttl")).andExpect(jsonPath("$.ttlSeconds").value(30));
    }

    @Test
    void expireValidatesPositiveTtl() throws Exception {
        mvc.perform(post("/cache/ns/k/expire").contentType(MediaType.APPLICATION_JSON).content("{\"ttlSeconds\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.ttlSeconds").value("ttlSeconds must be positive"));
        verifyNoInteractions(store);
    }

    @Test
    void expireAppliedOrNotFound() throws Exception {
        when(store.expire("ns", "k", 60)).thenReturn(true);
        when(store.expire("ns", "gone", 60)).thenReturn(false);

        mvc.perform(post("/cache/ns/k/expire").contentType(MediaType.APPLICATION_JSON).content("{\"ttlSeconds\":60}"))
                .andExpect(status().isOk());
        mvc.perform(post("/cache/ns/gone/expire").contentType(MediaType.APPLICATION_JSON).content("{\"ttlSeconds\":60}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void incrementDefaultsToOneAndAcceptsDelta() throws Exception {
        when(store.incrementBy("ns", "c", 1)).thenReturn(1L);
        when(store.incrementBy("ns", "c", 10)).thenReturn(11L);

        mvc.perform(post("/cache/ns/c/increment")).andExpect(jsonPath("$.value").value(1));
        mvc.perform(post("/cache/ns/c/increment").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":10}"))
                .andExpect(jsonPath("$.value").value(11));
    }

    @Test
    void decrementNegatesDelta() throws Exception {
        when(store.incrementBy("ns", "c", -1)).thenReturn(4L);
        when(store.incrementBy("ns", "c", -3)).thenReturn(1L);

        mvc.perform(post("/cache/ns/c/decrement")).andExpect(jsonPath("$.value").value(4));
        mvc.perform(post("/cache/ns/c/decrement").contentType(MediaType.APPLICATION_JSON).content("{\"delta\":3}"))
                .andExpect(jsonPath("$.value").value(1));
    }

    @Test
    void hashFieldEndpoints() throws Exception {
        when(store.hashGetField("ns", "h", "f")).thenReturn(Optional.of("val"));
        when(store.hashGetField("ns", "h", "none")).thenReturn(Optional.empty());
        when(store.hashDeleteField("ns", "h", "f")).thenReturn(true);
        when(store.hashDeleteField("ns", "h", "none")).thenReturn(false);

        mvc.perform(put("/cache/ns/h/hash").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"field\":\"f\",\"value\":\"val\"}"))
                .andExpect(status().isOk());
        verify(store).hashSetField("ns", "h", "f", "val", SerializationFormat.JSON);

        mvc.perform(get("/cache/ns/h/hash/f")).andExpect(jsonPath("$.found").value(true)).andExpect(jsonPath("$.value").value("val"));
        mvc.perform(get("/cache/ns/h/hash/none")).andExpect(jsonPath("$.found").value(false));
        mvc.perform(delete("/cache/ns/h/hash/f")).andExpect(status().isNoContent());
        mvc.perform(delete("/cache/ns/h/hash/none")).andExpect(status().isNotFound());
    }

    @Test
    void hashSetRequiresField() throws Exception {
        mvc.perform(put("/cache/ns/h/hash").contentType(MediaType.APPLICATION_JSON).content("{\"value\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkGetRejectsEmptyList() throws Exception {
        mvc.perform(post("/cache/bulk/get").contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkGetAndPutDelegate() throws Exception {
        when(store.bulkGet(any())).thenReturn(new BulkGetResponse(List.of(), List.of(new CacheKeyRef("ns", "k"))));
        when(store.bulkPut(any())).thenReturn(new BulkPutResponse(List.of(new BulkPutResponse.BulkPutResult("ns", "k", true, null))));

        mvc.perform(post("/cache/bulk/get").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[{\"namespace\":\"ns\",\"key\":\"k\"}]}"))
                .andExpect(jsonPath("$.missing[0].key").value("k"));
        mvc.perform(post("/cache/bulk/put").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\":[{\"namespace\":\"ns\",\"key\":\"k\",\"value\":1}]}"))
                .andExpect(jsonPath("$.results[0].success").value(true));
    }

    @Test
    void bulkGetValidatesRefs() throws Exception {
        mvc.perform(post("/cache/bulk/get").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keys\":[{\"namespace\":\"\",\"key\":\"k\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evictionEndpoints() throws Exception {
        when(eviction.evictNamespace("ns")).thenReturn(5L);
        when(eviction.evictPattern("ns:user:*")).thenReturn(2L);

        mvc.perform(delete("/cache/namespace/ns"))
                .andExpect(jsonPath("$.scope").value("namespace:ns")).andExpect(jsonPath("$.keysEvicted").value(5));
        mvc.perform(delete("/cache/pattern/{p}", "ns:user:*"))
                .andExpect(jsonPath("$.scope").value("pattern:ns:user:*")).andExpect(jsonPath("$.keysEvicted").value(2));
    }

    @Test
    void statsEndpoint() throws Exception {
        when(stats.stats()).thenReturn(new StatsResponse(3, 1, 0.75, 10, 2048, 1, 0));

        mvc.perform(get("/cache/stats"))
                .andExpect(jsonPath("$.hitRatio").value(0.75))
                .andExpect(jsonPath("$.totalKeys").value(10));
    }
}
