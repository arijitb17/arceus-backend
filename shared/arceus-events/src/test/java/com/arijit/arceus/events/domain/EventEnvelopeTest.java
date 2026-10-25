package com.arijit.arceus.events.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void ofGeneratesEventIdAndCopiesFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> payload = Map.of("k", "v");

        EventEnvelope env = EventEnvelope.of("USER_REGISTERED", now, "auth-service",
                "corr-1", "user-1", "10.0.0.1", "JUnit", payload);

        assertThat(UUID.fromString(env.eventId())).isNotNull();
        assertThat(env.eventType()).isEqualTo("USER_REGISTERED");
        assertThat(env.occurredAt()).isEqualTo(now);
        assertThat(env.sourceService()).isEqualTo("auth-service");
        assertThat(env.correlationId()).isEqualTo("corr-1");
        assertThat(env.userId()).isEqualTo("user-1");
        assertThat(env.ipAddress()).isEqualTo("10.0.0.1");
        assertThat(env.userAgent()).isEqualTo("JUnit");
        assertThat(env.payload()).isEqualTo(payload);
    }

    @Test
    void ofGeneratesDistinctEventIds() {
        EventEnvelope a = EventEnvelope.of("T", Instant.now(), "s", null, null, null, null, Map.of());
        EventEnvelope b = EventEnvelope.of("T", Instant.now(), "s", null, null, null, null, Map.of());
        assertThat(a.eventId()).isNotEqualTo(b.eventId());
    }

    @Test
    void acceptsNullOptionalFields() {
        EventEnvelope env = EventEnvelope.of("T", Instant.now(), "s", null, null, null, null, null);
        assertThat(env.correlationId()).isNull();
        assertThat(env.payload()).isNull();
    }
}
