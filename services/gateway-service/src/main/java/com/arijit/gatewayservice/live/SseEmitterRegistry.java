package com.arijit.gatewayservice.live;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every currently-connected SSE client so {@link LiveUpdatesEventListener}
 * has somewhere to push each domain event as it arrives. Deliberately
 * in-memory and per-instance: if the Gateway ever runs as more than one
 * replica, each replica's clients only see events delivered to that
 * replica's own queue consumer - fine for now (RabbitMQ fans the same
 * exchange binding out to however many competing consumers exist, so no
 * client misses events, they just may connect to a different instance on
 * reconnect), but worth knowing if this ever needs cross-instance fan-out.
 * <p>
 * Also sends a periodic heartbeat (see {@link #sendHeartbeats()}) - without
 * one, a genuinely idle connection (no domain events at all) sends zero
 * bytes after the initial response, which both looks indistinguishable
 * from "still connecting" to a client and risks an intermediary proxy/load
 * balancer silently timing out and dropping it.
 */
@Component
@Slf4j
public class SseEmitterRegistry {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter register() {
        // No timeout (0L): a live-updates connection is meant to be
        // long-lived. The client (or an intermediary proxy) closing the
        // connection is what actually ends it, via the completion/timeout
        // callbacks below.
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));
        return emitter;
    }

    public void broadcast(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException ex) {
                // Client disconnected without a clean completion callback
                // firing yet (e.g. it just vanished) - drop it, and let the
                // next broadcast to a genuinely-live emitter succeed.
                emitters.remove(emitter);
            }
        }
    }

    /**
     * A named "heartbeat" event (not a bare SSE comment) so the frontend's
     * hand-rolled parser - which only recognizes `event:`/`data:` frames,
     * see StreamSse's javadoc - can treat it exactly like any other event
     * for the purpose of "the connection is definitely alive", without
     * having to special-case comment-only frames.
     */
    @Scheduled(fixedRate = 20_000)
    public void sendHeartbeats() {
        if (emitters.isEmpty()) {
            return;
        }
        broadcast("heartbeat", java.util.Map.of("at", java.time.Instant.now().toString()));
    }

    public int connectedClients() {
        return emitters.size();
    }
}
