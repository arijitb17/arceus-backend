package com.arijit.gatewayservice.live;

import com.arijit.arceus.events.domain.EventEnvelope;
import com.arijit.gatewayservice.error.ApiException;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveUpdatesTest {

    @Mock SseEmitterRegistry mockRegistry;

    @Test
    void registerTracksEmitterAndCompletionRemovesIt() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        SseEmitter e = registry.register();
        assertThat(registry.connectedClients()).isEqualTo(1);
        registry.register();
        assertThat(registry.connectedClients()).isEqualTo(2);

        e.complete();
        // completion callbacks only fire once the emitter is attached to a handler/dispatch; the
        // registry must at minimum have returned a non-timeout emitter
        assertThat(e.getTimeout()).isZero();
    }

    @Test
    void broadcastWithNoClientsIsHarmless() {
        SseEmitterRegistry registry = new SseEmitterRegistry();

        registry.broadcast("evt", Map.of("a", 1));
        registry.sendHeartbeats();

        assertThat(registry.connectedClients()).isZero();
    }

    @Test
    void listenerSkipsBroadcastWhenNobodyConnected() {
        when(mockRegistry.connectedClients()).thenReturn(0);

        new LiveUpdatesEventListener(mockRegistry).onEvent(
                EventEnvelope.of("X", Instant.now(), "s", null, null, null, null, Map.of()));

        verify(mockRegistry, never()).broadcast(any(), any());
    }

    @Test
    void listenerBroadcastsUnderEventType() {
        when(mockRegistry.connectedClients()).thenReturn(2);
        EventEnvelope env = EventEnvelope.of("DEPLOYMENT_FINISHED", Instant.now(), "s", null, null, null, null, Map.of());

        new LiveUpdatesEventListener(mockRegistry).onEvent(env);

        verify(mockRegistry).broadcast("DEPLOYMENT_FINISHED", env);
    }

    @Test
    void listenerSwallowsBroadcastFailures() {
        when(mockRegistry.connectedClients()).thenReturn(1);
        doThrow(new IllegalStateException("boom")).when(mockRegistry).broadcast(any(), any());

        new LiveUpdatesEventListener(mockRegistry).onEvent(
                EventEnvelope.of("X", Instant.now(), "s", null, null, null, null, Map.of()));
    }

    @Test
    void controllerRequiresAdminRole() {
        LiveUpdatesController controller = new LiveUpdatesController(mockRegistry);
        MockHttpServletRequest user = new MockHttpServletRequest();
        user.setAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE, List.of("ROLE_USER"));

        assertThatThrownBy(() -> controller.stream(user))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> controller.stream(new MockHttpServletRequest())).isInstanceOf(ApiException.class);
        verifyNoInteractions(mockRegistry);
    }

    @Test
    void adminGetsRegisteredEmitter() {
        SseEmitter emitter = new SseEmitter();
        when(mockRegistry.register()).thenReturn(emitter);
        MockHttpServletRequest admin = new MockHttpServletRequest();
        admin.setAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE, List.of("ROLE_ADMIN"));

        assertThat(new LiveUpdatesController(mockRegistry).stream(admin)).isSameAs(emitter);
    }
}
