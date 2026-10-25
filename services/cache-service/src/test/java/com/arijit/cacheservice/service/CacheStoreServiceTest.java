package com.arijit.cacheservice.service;

import com.arijit.cacheservice.CacheTestFixtures;
import com.arijit.cacheservice.compression.CompressionService;
import com.arijit.cacheservice.config.CacheProperties;
import com.arijit.cacheservice.core.CacheEntryMetadata;
import com.arijit.cacheservice.core.CacheKeyBuilder;
import com.arijit.cacheservice.core.NamespaceValidator;
import com.arijit.cacheservice.dto.*;
import com.arijit.cacheservice.exception.ApiException;
import com.arijit.cacheservice.serialization.BinaryCacheSerializer;
import com.arijit.cacheservice.serialization.CacheSerializerFactory;
import com.arijit.cacheservice.serialization.JsonCacheSerializer;
import com.arijit.cacheservice.serialization.StringCacheSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.*;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises CacheStoreService with the real key builder / serializers / compression / TTL logic and
 * mocked Redis templates, so the assertions are about what would be written to Redis.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class CacheStoreServiceTest {

    @Mock
    RedisTemplate<String, byte[]> cacheRedis;
    @Mock
    StringRedisTemplate stringRedis;
    @Mock
    NamespaceValidator validator;
    @Mock
    CacheStatsService stats;
    @Mock
    CacheEvictionService eviction;
    @Mock
    ValueOperations<String, byte[]> valueOps;
    @Mock
    HashOperations<String, Object, Object> dataHashOps;
    @Mock
    HashOperations<String, Object, Object> metaHashOps;
    @Mock
    ListOperations<String, byte[]> listOps;
    @Mock
    SetOperations<String, byte[]> setOps;
    @Mock
    ZSetOperations<String, byte[]> zsetOps;

    private final ObjectMapper mapper = new ObjectMapper();
    private final CompressionService compression = new CompressionService();
    private CacheProperties props;
    private CacheStoreService service;

    private static final String NS = "session";
    private static final String KEY = "user:1";
    private static final String DATA = "session:user:1";
    private static final String META = "session:user:1:__meta";

    @BeforeEach
    void setUp() {
        props = CacheTestFixtures.properties();
        build(props);
        lenient().when(cacheRedis.opsForValue()).thenReturn(valueOps);
        lenient().doReturn(dataHashOps).when(cacheRedis).opsForHash();
        lenient().when(cacheRedis.opsForList()).thenReturn(listOps);
        lenient().when(cacheRedis.opsForSet()).thenReturn(setOps);
        lenient().when(cacheRedis.opsForZSet()).thenReturn(zsetOps);
        lenient().doReturn(metaHashOps).when(stringRedis).opsForHash();
    }

    private void build(CacheProperties properties) {
        this.props = properties;
        service = new CacheStoreService(cacheRedis, stringRedis, new CacheKeyBuilder(), validator,
                new CacheTtlService(properties), stats, eviction,
                new CacheSerializerFactory(new JsonCacheSerializer(mapper), new StringCacheSerializer(), new BinaryCacheSerializer()),
                compression, properties, mapper);
    }

    private CacheRequest req(Object value) {
        return new CacheRequest(NS, KEY, value, null, null, null, null, null, null, null);
    }

    private CacheRequest req(Object value, CacheDataType type) {
        return new CacheRequest(NS, KEY, value, type, null, null, null, null, null, null);
    }

    private void apiError(Runnable r, HttpStatus status) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    private Map<Object, Object> storedMeta(CacheDataType type, SerializationFormat ser, boolean compressed,
                                           TtlPolicy ttl, long ttlSeconds, Instant created) {
        return new HashMap<Object, Object>(new CacheEntryMetadata(type, ser, compressed, ttl, ttlSeconds,
                CachePolicy.CACHE_ASIDE, created, created).toRedisHash());
    }

    private Map<String, String> capturedMeta() {
        ArgumentCaptor<Map> cap = ArgumentCaptor.forClass(Map.class);
        verify(metaHashOps).putAll(eq(META), cap.capture());
        return cap.getValue();
    }

    @Nested
    class Write {

        @Test
        void stringJsonValueIsStoredAsJsonBytesWithMetadata() {
            CacheResponse r = service.set(req(Map.of("name", "ash")));

            ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
            verify(valueOps).set(eq(DATA), bytes.capture());
            assertThat(new String(bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"ash\"}");
            Map<String, String> meta = capturedMeta();
            assertThat(meta).containsEntry("type", "STRING").containsEntry("serialization", "JSON")
                    .containsEntry("compressed", "false").containsEntry("ttlPolicy", "NO_EXPIRY");
            verify(validator).validate(NS);
            assertThat(r.value()).isEqualTo(Map.of("name", "ash"));
            assertThat(r.type()).isEqualTo(CacheDataType.STRING);
        }

        @Test
        void noExpiryPersistsBothKeys() {
            service.set(req("x"));

            verify(stringRedis).persist(DATA);
            verify(stringRedis).persist(META);
            verify(stringRedis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        void customTtlAppliesExpiryToDataAndMeta() {
            service.set(new CacheRequest(NS, KEY, "x", null, null, null, TtlPolicy.CUSTOM, 120L, null, null));

            verify(stringRedis).expire(DATA, 120L, TimeUnit.SECONDS);
            verify(stringRedis).expire(META, 120L, TimeUnit.SECONDS);
            assertThat(capturedMeta()).containsEntry("ttlSeconds", "120").containsEntry("ttlPolicy", "CUSTOM");
        }

        @Test
        void stringSerializationRequiresStringValue() {
            CacheRequest r = new CacheRequest(NS, KEY, 123, null, SerializationFormat.STRING, null, null, null, null, null);

            apiError(() -> service.set(r), HttpStatus.BAD_REQUEST);
            verifyNoInteractions(valueOps);
        }

        @Test
        void binarySerializationStoresDecodedBytes() {
            String b64 = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

            service.set(new CacheRequest(NS, KEY, b64, null, SerializationFormat.BINARY, null, null, null, null, null));

            ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
            verify(valueOps).set(eq(DATA), bytes.capture());
            assertThat(bytes.getValue()).containsExactly(1, 2, 3);
        }

        @Test
        void nullValueRejected() {
            apiError(() -> service.set(req(null)), HttpStatus.BAD_REQUEST);
        }

        @Test
        void blankKeyRejectedAfterNamespaceValidation() {
            apiError(() -> service.set(new CacheRequest(NS, " ", "v", null, null, null, null, null, null, null)),
                    HttpStatus.BAD_REQUEST);
            verify(validator).validate(NS);
        }

        @Test
        void namespaceValidationFailurePropagates() {
            doThrow(new ApiException("nope", HttpStatus.FORBIDDEN)).when(validator).validate(NS);

            apiError(() -> service.set(req("v")), HttpStatus.FORBIDDEN);
            verifyNoInteractions(valueOps);
        }

        @Test
        void oversizedValueIs413() {
            build(CacheTestFixtures.properties(10, false, 8192, 3600));

            apiError(() -> service.set(req("this value is definitely longer than ten bytes")), HttpStatus.PAYLOAD_TOO_LARGE);
            verifyNoInteractions(valueOps);
        }

        @Test
        void forcedCompressionStoresGzipAndFlagsMetadata() {
            service.set(new CacheRequest(NS, KEY, "hello hello hello", null, SerializationFormat.STRING, true,
                    null, null, null, null));

            ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
            verify(valueOps).set(eq(DATA), bytes.capture());
            assertThat(compression.decompress(bytes.getValue())).isEqualTo("hello hello hello".getBytes(StandardCharsets.UTF_8));
            assertThat(capturedMeta()).containsEntry("compressed", "true");
        }

        @Test
        void autoCompressionKicksInAtThreshold() {
            build(CacheTestFixtures.properties(5_000_000, true, 20, 3600));

            service.set(new CacheRequest(NS, KEY, "x".repeat(50), null, SerializationFormat.STRING, null, null, null, null, null));

            assertThat(capturedMeta()).containsEntry("compressed", "true");
        }

        @Test
        void belowThresholdOrDisabledStaysUncompressed() {
            build(CacheTestFixtures.properties(5_000_000, false, 1, 3600));

            service.set(new CacheRequest(NS, KEY, "x".repeat(50), null, SerializationFormat.STRING, null, null, null, null, null));

            assertThat(capturedMeta()).containsEntry("compressed", "false");
        }

        @Test
        void hashValueIsReplacedFieldByField() {
            service.set(req(Map.of("a", 1), CacheDataType.HASH));

            verify(cacheRedis).delete(DATA);
            ArgumentCaptor<Map> cap = ArgumentCaptor.forClass(Map.class);
            verify(dataHashOps).putAll(eq(DATA), cap.capture());
            Map<String, byte[]> written = cap.getValue();
            assertThat(new String(written.get("a"), StandardCharsets.UTF_8)).isEqualTo("1");
            assertThat(capturedMeta()).containsEntry("type", "HASH");
        }

        @Test
        void emptyHashStillClearsAndSkipsPutAll() {
            service.set(req(Map.of(), CacheDataType.HASH));

            verify(cacheRedis).delete(DATA);
            verify(dataHashOps, never()).putAll(anyString(), anyMap());
        }

        @Test
        void listIsRightPushed() {
            service.set(req(List.of("a", 2), CacheDataType.LIST));

            verify(cacheRedis).delete(DATA);
            ArgumentCaptor<Collection> cap = ArgumentCaptor.forClass(Collection.class);
            verify(listOps).rightPushAll(eq(DATA), cap.capture());
            assertThat(cap.getValue()).hasSize(2);
        }

        @Test
        void setMembersAreAdded() {
            service.set(req(List.of("a", "b"), CacheDataType.SET));

            verify(setOps).add(eq(DATA), any(byte[][].class));
        }

        @Test
        void sortedSetAddsMembersWithScores() {
            service.set(req(List.of(Map.of("member", "a", "score", 1.5), Map.of("member", "b", "score", 2)),
                    CacheDataType.SORTED_SET));

            verify(zsetOps).add(eq(DATA), any(byte[].class), eq(1.5));
            verify(zsetOps).add(eq(DATA), any(byte[].class), eq(2.0));
        }

        @Test
        void sortedSetEntryMissingScoreRejected() {
            apiError(() -> service.set(req(List.of(Map.of("member", "a")), CacheDataType.SORTED_SET)),
                    HttpStatus.BAD_REQUEST);
            verify(zsetOps, never()).add(anyString(), any(byte[].class), anyDouble());
        }

        @Test
        void hashWithNonObjectValueRejected() {
            assertThatThrownBy(() -> service.set(req("not-an-object", CacheDataType.HASH)))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class Update {

        @Test
        void missingKeyIs404() {
            when(stringRedis.hasKey(META)).thenReturn(false);

            apiError(() -> service.update(req("v")), HttpStatus.NOT_FOUND);
            verifyNoInteractions(valueOps);
        }

        @Test
        void existingKeyKeepsCreatedAtAndBumpsUpdatedAt() {
            Instant created = Instant.parse("2025-01-01T00:00:00Z");
            when(stringRedis.hasKey(META)).thenReturn(true);
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.JSON,
                    false, TtlPolicy.NO_EXPIRY, 0, created));

            CacheResponse r = service.update(req("v", CacheDataType.STRING));

            assertThat(r.createdAt()).isEqualTo(created);
            assertThat(r.updatedAt()).isAfter(created);
            assertThat(capturedMeta()).containsEntry("createdAt", created.toString());
        }
    }

    @Nested
    class Read {

        @Test
        void missWhenNoMetadata() {
            when(metaHashOps.entries(META)).thenReturn(new HashMap<>());

            assertThat(service.get(NS, KEY)).isEmpty();

            verify(stats).recordMiss(NS, KEY);
            verify(stats, never()).recordHit(anyString(), anyString());
        }

        @Test
        void jsonStringIsParsedBackToStructure() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.JSON,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(valueOps.get(DATA)).thenReturn("{\"a\":[1,2]}".getBytes(StandardCharsets.UTF_8));
            when(stringRedis.getExpire(DATA, TimeUnit.SECONDS)).thenReturn(-1L);

            CacheResponse r = service.get(NS, KEY).orElseThrow();

            assertThat(r.value()).isEqualTo(Map.of("a", List.of(1, 2)));
            assertThat(r.ttlSecondsRemaining()).isEqualTo(-1);
            verify(stats).recordHit(NS, KEY);
        }

        @Test
        void plainStringReturnedAsIs() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(valueOps.get(DATA)).thenReturn("hello".getBytes(StandardCharsets.UTF_8));

            assertThat(service.get(NS, KEY).orElseThrow().value()).isEqualTo("hello");
        }

        @Test
        void compressedValuesAreDecompressed() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    true, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(valueOps.get(DATA)).thenReturn(compression.compress("zipped".getBytes(StandardCharsets.UTF_8)));

            assertThat(service.get(NS, KEY).orElseThrow().value()).isEqualTo("zipped");
        }

        @Test
        void binaryValuesAreReturnedBase64() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.BINARY,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(valueOps.get(DATA)).thenReturn(new byte[]{9, 8});

            assertThat(service.get(NS, KEY).orElseThrow().value()).isEqualTo(Base64.getEncoder().encodeToString(new byte[]{9, 8}));
        }

        @Test
        void metadataPresentButDataGoneYieldsNullValueStillHit() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(valueOps.get(DATA)).thenReturn(null);

            assertThat(service.get(NS, KEY).orElseThrow().value()).isNull();
        }

        @Test
        void slidingTtlIsRefreshedOnRead() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    false, TtlPolicy.SLIDING, 300, Instant.now()));
            when(valueOps.get(DATA)).thenReturn("v".getBytes(StandardCharsets.UTF_8));
            when(stringRedis.getExpire(DATA, TimeUnit.SECONDS)).thenReturn(299L);

            CacheResponse r = service.get(NS, KEY).orElseThrow();

            verify(stringRedis).expire(DATA, 300L, TimeUnit.SECONDS);
            verify(stringRedis).expire(META, 300L, TimeUnit.SECONDS);
            assertThat(r.ttlSecondsRemaining()).isEqualTo(299);
        }

        @Test
        void nonSlidingTtlNotRefreshed() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    false, TtlPolicy.CUSTOM, 300, Instant.now()));
            when(valueOps.get(DATA)).thenReturn("v".getBytes(StandardCharsets.UTF_8));

            service.get(NS, KEY);

            verify(stringRedis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        void nullTtlFromRedisReportedAsMinusOne() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(valueOps.get(DATA)).thenReturn("v".getBytes(StandardCharsets.UTF_8));
            when(stringRedis.getExpire(DATA, TimeUnit.SECONDS)).thenReturn(null);

            assertThat(service.get(NS, KEY).orElseThrow().ttlSecondsRemaining()).isEqualTo(-1);
        }

        @Test
        void hashEntriesDecoded() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.HASH, SerializationFormat.JSON,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            Map<Object, Object> raw = new HashMap<>();
            raw.put("a", "1".getBytes(StandardCharsets.UTF_8));
            raw.put("b", "\"txt\"".getBytes(StandardCharsets.UTF_8));
            when(dataHashOps.entries(DATA)).thenReturn(raw);

            assertThat(service.get(NS, KEY).orElseThrow().value()).isEqualTo(Map.of("a", 1, "b", "txt"));
        }

        @Test
        void listAndSetDecoded() {
            when(metaHashOps.entries(META)).thenReturn(
                    storedMeta(CacheDataType.LIST, SerializationFormat.JSON, false, TtlPolicy.NO_EXPIRY, 0, Instant.now()),
                    storedMeta(CacheDataType.SET, SerializationFormat.JSON, false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(listOps.range(DATA, 0, -1)).thenReturn(List.of("1".getBytes(StandardCharsets.UTF_8), "\"x\"".getBytes(StandardCharsets.UTF_8)));
            when(setOps.members(DATA)).thenReturn(Set.of("true".getBytes(StandardCharsets.UTF_8)));

            assertThat(service.get(NS, KEY).orElseThrow().value()).isEqualTo(List.of(1, "x"));
            assertThat(service.get(NS, KEY).orElseThrow().value()).isEqualTo(List.of(true));
        }

        @Test
        void sortedSetDecodedWithScores() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.SORTED_SET, SerializationFormat.JSON,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            ZSetOperations.TypedTuple<byte[]> tuple = mock(ZSetOperations.TypedTuple.class);
            when(tuple.getValue()).thenReturn("\"m\"".getBytes(StandardCharsets.UTF_8));
            when(tuple.getScore()).thenReturn(4.5);
            when(zsetOps.rangeWithScores(DATA, 0, -1)).thenReturn(new LinkedHashSet<>(List.of(tuple)));

            Object value = service.get(NS, KEY).orElseThrow().value();

            assertThat((List<Map<String, Object>>) value).containsExactly(Map.of("member", "m", "score", 4.5));
        }
    }

    @Nested
    class KeyOperations {

        @Test
        void deleteDelegatesToEviction() {
            when(eviction.evictKey(NS, KEY)).thenReturn(true);

            assertThat(service.delete(NS, KEY)).isTrue();
        }

        @Test
        void existsChecksMetaKey() {
            when(stringRedis.hasKey(META)).thenReturn(true, false, null);

            assertThat(service.exists(NS, KEY)).isTrue();
            assertThat(service.exists(NS, KEY)).isFalse();
            assertThat(service.exists(NS, KEY)).isFalse();
        }

        @Test
        void ttlReturnsRedisValueOrMinusTwo() {
            when(stringRedis.getExpire(DATA, TimeUnit.SECONDS)).thenReturn(42L, (Long) null);

            assertThat(service.ttl(NS, KEY)).isEqualTo(42);
            assertThat(service.ttl(NS, KEY)).isEqualTo(-2);
        }

        @Test
        void expireOnMissingKeyReturnsFalse() {
            when(stringRedis.hasKey(META)).thenReturn(false);

            assertThat(service.expire(NS, KEY, 60)).isFalse();

            verify(stringRedis, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
        }

        @Test
        void expireOnExistingKeySetsBothTtls() {
            when(stringRedis.hasKey(META)).thenReturn(true);

            assertThat(service.expire(NS, KEY, 60)).isTrue();

            verify(stringRedis).expire(DATA, 60L, TimeUnit.SECONDS);
            verify(stringRedis).expire(META, 60L, TimeUnit.SECONDS);
        }
    }

    @Nested
    class Increment {

        @Test
        void createsStringMetadataForNewCounter() {
            when(metaHashOps.entries(META)).thenReturn(new HashMap<>());
            when(valueOps.increment(DATA, 5L)).thenReturn(5L);

            assertThat(service.incrementBy(NS, KEY, 5)).isEqualTo(5);

            assertThat(capturedMeta()).containsEntry("type", "STRING").containsEntry("serialization", "STRING");
        }

        @Test
        void existingCounterMetadataTouched() {
            Instant created = Instant.parse("2025-05-05T00:00:00Z");
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.STRING,
                    false, TtlPolicy.NO_EXPIRY, 0, created));
            when(valueOps.increment(DATA, -2L)).thenReturn(8L);

            assertThat(service.incrementBy(NS, KEY, -2)).isEqualTo(8);

            assertThat(capturedMeta()).containsEntry("createdAt", created.toString());
        }

        @Test
        void nonStringEntryConflicts() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.LIST, SerializationFormat.JSON,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));

            apiError(() -> service.incrementBy(NS, KEY, 1), HttpStatus.CONFLICT);
            verify(valueOps, never()).increment(anyString(), anyLong());
        }

        @Test
        void nullIncrementResultTreatedAsZero() {
            when(metaHashOps.entries(META)).thenReturn(new HashMap<>());
            when(valueOps.increment(DATA, 1L)).thenReturn(null);

            assertThat(service.incrementBy(NS, KEY, 1)).isZero();
        }
    }

    @Nested
    class HashFields {

        @Test
        void setFieldCreatesHashMetadataWhenNew() {
            when(metaHashOps.entries(META)).thenReturn(new HashMap<>());

            service.hashSetField(NS, KEY, "f", Map.of("x", 1), SerializationFormat.JSON);

            ArgumentCaptor<Object> bytes = ArgumentCaptor.forClass(Object.class);
            verify(dataHashOps).put(eq(DATA), eq("f"), bytes.capture());
            assertThat(new String((byte[]) bytes.getValue(), StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
            assertThat(capturedMeta()).containsEntry("type", "HASH");
        }

        @Test
        void setFieldOnNonHashConflicts() {
            when(metaHashOps.entries(META)).thenReturn(storedMeta(CacheDataType.STRING, SerializationFormat.JSON,
                    false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));

            apiError(() -> service.hashSetField(NS, KEY, "f", 1, SerializationFormat.JSON), HttpStatus.CONFLICT);
        }

        @Test
        void getFieldHitAndMiss() {
            when(dataHashOps.get(DATA, "present")).thenReturn("42".getBytes(StandardCharsets.UTF_8));
            when(dataHashOps.get(DATA, "absent")).thenReturn(null);

            assertThat(service.hashGetField(NS, KEY, "present")).contains(42);
            assertThat(service.hashGetField(NS, KEY, "absent")).isEmpty();

            verify(stats).recordHit(NS, KEY + ".present");
            verify(stats).recordMiss(NS, KEY + ".absent");
        }

        @Test
        void deleteFieldReportsWhetherRemoved() {
            when(dataHashOps.delete(DATA, "f")).thenReturn(1L, 0L, null);

            assertThat(service.hashDeleteField(NS, KEY, "f")).isTrue();
            assertThat(service.hashDeleteField(NS, KEY, "f")).isFalse();
            assertThat(service.hashDeleteField(NS, KEY, "f")).isFalse();
        }
    }

    @Nested
    class Bulk {

        @Test
        void bulkGetSeparatesFoundAndMissing() {
            when(metaHashOps.entries("session:hit:__meta")).thenReturn(storedMeta(CacheDataType.STRING,
                    SerializationFormat.STRING, false, TtlPolicy.NO_EXPIRY, 0, Instant.now()));
            when(metaHashOps.entries("session:miss:__meta")).thenReturn(new HashMap<>());
            when(valueOps.get("session:hit")).thenReturn("v".getBytes(StandardCharsets.UTF_8));

            BulkGetResponse r = service.bulkGet(List.of(new CacheKeyRef(NS, "hit"), new CacheKeyRef(NS, "miss")));

            assertThat(r.found()).extracting(CacheResponse::key).containsExactly("hit");
            assertThat(r.missing()).containsExactly(new CacheKeyRef(NS, "miss"));
        }

        @Test
        void bulkPutReportsPerEntryOutcomeWithoutAborting() {
            lenient().doAnswer(inv -> {
                if ("bad".equals(inv.getArgument(0))) {
                    throw new ApiException("forbidden ns", HttpStatus.FORBIDDEN);
                }
                return null;
            }).when(validator).validate(anyString());

            lenient().doAnswer(inv -> {
                if ("session:c".equals(inv.getArgument(0))) {
                    throw new IllegalStateException("redis exploded");
                }
                return null;
            }).when(valueOps).set(anyString(), any(byte[].class));

            CacheRequest ok = new CacheRequest(NS, "a", "v", null, SerializationFormat.STRING, null, null, null, null, null);
            CacheRequest rejected = new CacheRequest("bad", "b", "v", null, null, null, null, null, null, null);
            CacheRequest crashes = new CacheRequest(NS, "c", "v", null, SerializationFormat.STRING, null, null, null, null, null);

            BulkPutResponse r = service.bulkPut(List.of(ok, rejected, crashes));

            assertThat(r.results()).extracting(BulkPutResponse.BulkPutResult::success).containsExactly(true, false, false);
            assertThat(r.results().get(1).error()).isEqualTo("forbidden ns");
            assertThat(r.results().get(2).error()).isEqualTo("internal error");
            assertThat(r.results().get(0).error()).isNull();
        }
    }
}