package com.arijit.gatewayservice.live;

import com.arijit.arceus.events.domain.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the platform's catch-all domain-event queue - every service
 * already publishes onto ArceusEventsTopology's shared exchange - and
 * re-emits each envelope to every connected SSE client via
 * {@link SseEmitterRegistry}. This is the entire "fan-out" layer: no
 * per-service changes were needed to make any of this data live.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiveUpdatesEventListener {

    private final SseEmitterRegistry registry;

    @RabbitListener(queues = LiveUpdatesRabbitConfig.QUEUE)
    public void onEvent(EventEnvelope envelope) {
        if (registry.connectedClients() == 0) {
            // Nothing to fan out to - skip the (cheap but non-zero) work of
            // building an SSE payload for zero recipients.
            return;
        }
        try {
            registry.broadcast(envelope.eventType(), envelope);
        } catch (Exception ex) {
            // A malformed/unexpected envelope shouldn't take down the
            // listener for every other event still queued behind it.
            log.warn("Failed to broadcast live-update event {}: {}", envelope.eventType(), ex.getMessage());
        }
    }
}
