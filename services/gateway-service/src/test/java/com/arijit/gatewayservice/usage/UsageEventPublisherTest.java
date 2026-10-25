package com.arijit.gatewayservice.usage;

import com.arijit.arceus.events.domain.ArceusEventsTopology;
import com.arijit.arceus.events.domain.EventEnvelope;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageEventPublisherTest {

    @Mock RabbitTemplate rabbit;
    @InjectMocks UsageEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "sourceService", "gateway-service");
    }

    @Test
    void publishesUsageEnvelope() {
        publisher.recordRequest("org", "proj", "prod", "CACHE", 200);

        ArgumentCaptor<EventEnvelope> cap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbit).convertAndSend(eq(ArceusEventsTopology.EVENTS_EXCHANGE),
                eq(ArceusEventsTopology.RK_USAGE_REQUEST_RECORDED), cap.capture());
        EventEnvelope e = cap.getValue();
        assertThat(e.eventType()).isEqualTo("USAGE_REQUEST_RECORDED");
        assertThat(e.sourceService()).isEqualTo("gateway-service");
        assertThat(e.payload()).containsEntry("organizationId", "org").containsEntry("projectId", "proj")
                .containsEntry("environment", "prod").containsEntry("route", "CACHE").containsEntry("statusCode", 200);
    }

    @Test
    void brokerFailureNeverPropagates() {
        doThrow(new AmqpException("down")).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.recordRequest("o", "p", "e", "AUTH", 500)).doesNotThrowAnyException();
    }
}
