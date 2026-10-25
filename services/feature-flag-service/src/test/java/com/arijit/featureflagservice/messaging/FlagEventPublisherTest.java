package com.arijit.featureflagservice.messaging;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.featureflagservice.FlagTestData;
import com.arijit.featureflagservice.entity.Environment;
import com.arijit.featureflagservice.entity.FeatureFlag;
import com.arijit.featureflagservice.entity.FlagStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagEventPublisherTest {

    @Mock RabbitTemplate rabbit;
    @InjectMocks FlagEventPublisher publisher;

    private final FeatureFlag flag = FlagTestData.booleanFlag("checkout", Environment.PRODUCTION, FlagStatus.ENABLED, true);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "sourceService", "feature-flag-service");
    }

    private void expect(BiConsumer<FlagEventPublisher, FeatureFlag> call, String routingKey, String type) {
        call.accept(publisher, flag);

        ArgumentCaptor<EventEnvelope> cap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbit).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE), eq(routingKey), cap.capture());
        EventEnvelope e = cap.getValue();
        assertThat(e.eventType()).isEqualTo(type);
        assertThat(e.sourceService()).isEqualTo("feature-flag-service");
        assertThat(e.userId()).isEqualTo("alice");
        assertThat(e.payload()).containsEntry("flagKey", "checkout").containsEntry("environment", "PRODUCTION")
                .containsEntry("type", "BOOLEAN").containsEntry("status", "ENABLED").containsEntry("rolloutPercentage", 100);
    }

    @Test
    void createdEvent() {
        expect((p, f) -> p.created(f, "alice"), ArceusEventsTopology.RK_FLAG_CREATED, "FEATURE_FLAG_CREATED");
    }

    @Test
    void updatedEvent() {
        expect((p, f) -> p.updated(f, "alice"), ArceusEventsTopology.RK_FLAG_UPDATED, "FEATURE_FLAG_UPDATED");
    }

    @Test
    void enabledEvent() {
        expect((p, f) -> p.enabled(f, "alice"), ArceusEventsTopology.RK_FLAG_ENABLED, "FEATURE_FLAG_ENABLED");
    }

    @Test
    void disabledEvent() {
        expect((p, f) -> p.disabled(f, "alice"), ArceusEventsTopology.RK_FLAG_DISABLED, "FEATURE_FLAG_DISABLED");
    }

    @Test
    void deletedEvent() {
        expect((p, f) -> p.deleted(f, "alice"), ArceusEventsTopology.RK_FLAG_DELETED, "FEATURE_FLAG_DELETED");
    }

    @Test
    void brokerOutageNeverPropagates() {
        doThrow(new AmqpException("down")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.created(flag, "alice")).doesNotThrowAnyException();
    }
}
