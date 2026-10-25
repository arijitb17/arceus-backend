package com.arijit.authservice.messaging;

import com.arijit.arceus.events.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DomainEventPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;
    private DomainEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new DomainEventPublisher(rabbitTemplate, mapper);
        ReflectionTestUtils.setField(publisher, "sourceService", "auth-service");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void wrapsEventInEnvelopeAndPublishesToEventsExchange() {
        UUID userId = UUID.randomUUID();
        MDC.put(CorrelationConstants.MDC_KEY, "corr-9");
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.1.1.1");
        req.addHeader("X-Forwarded-For", "8.8.8.8, 10.1.1.1");
        req.addHeader("User-Agent", "JUnit/5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        publisher.publish(LoginSuccessfulEvent.now(userId, "a@b.com"));

        ArgumentCaptor<EventEnvelope> cap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbitTemplate).convertAndSend(
                eq(ArceusEventsTopology.EVENTS_EXCHANGE), eq(ArceusEventsTopology.RK_LOGIN_SUCCESSFUL), cap.capture());
        EventEnvelope env = cap.getValue();
        assertThat(env.eventType()).isEqualTo("LOGIN_SUCCESSFUL");
        assertThat(env.sourceService()).isEqualTo("auth-service");
        assertThat(env.correlationId()).isEqualTo("corr-9");
        assertThat(env.userId()).isEqualTo(userId.toString());
        assertThat(env.ipAddress()).isEqualTo("8.8.8.8");
        assertThat(env.userAgent()).isEqualTo("JUnit/5");
        assertThat(env.payload()).containsEntry("email", "a@b.com");
    }

    @Test
    void usesRemoteAddrWithoutForwardedHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.2.2.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));

        publisher.publish(LoginFailedEvent.now("a@b.com", "BAD_CREDENTIALS"));

        ArgumentCaptor<EventEnvelope> cap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), cap.capture());
        assertThat(cap.getValue().ipAddress()).isEqualTo("10.2.2.2");
        assertThat(cap.getValue().userId()).isNull();
    }

    @Test
    void worksOutsideARequest() {
        publisher.publish(LoginFailedEvent.now("a@b.com", "x"));

        ArgumentCaptor<EventEnvelope> cap = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), cap.capture());
        assertThat(cap.getValue().ipAddress()).isNull();
        assertThat(cap.getValue().userAgent()).isNull();
        assertThat(cap.getValue().correlationId()).isNull();
    }

    @Test
    void brokerFailureIsSwallowed() {
        doThrow(new AmqpException("down")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatCode(() -> publisher.publish(LoginFailedEvent.now("a@b.com", "x"))).doesNotThrowAnyException();
    }
}
