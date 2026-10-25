package com.arijit.gatewayservice.live;

import com.arijit.arceus.events.domain.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LiveUpdatesEventListener {
    private final SseEmitterRegistry registry;

    @RabbitListener(queues = LiveUpdatesRabbitConfig.QUEUE)
    public void onEvent(EventEnvelope envelope) {
        if (registry.connectedClients() == 0) {
            return;
        }
        try {
            registry.broadcast(envelope.eventType(), envelope);
        } catch (Exception ex) {
            log.warn("Failed to broadcast live-update event {}: {}", envelope.eventType(), ex.getMessage());
        }
    }
}
