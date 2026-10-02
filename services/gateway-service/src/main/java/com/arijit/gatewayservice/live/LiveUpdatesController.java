package com.arijit.gatewayservice.live;

import com.arijit.gatewayservice.error.ApiException;
import com.arijit.gatewayservice.security.JwtAuthFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * One stream, every event type, admin-only for now - almost everything on
 * the shared event bus today is platform-ops data (deployments, jobs,
 * flags, monitoring), same as the REST endpoints that already expose it.
 * Client-side filtering by event type (the SSE {@code event:} field is
 * set to the event's eventType) is expected rather than a server-side
 * subscription API - simpler to start with, and this is a genuinely new
 * capability rather than a fix, so keeping the first version minimal on
 * purpose.
 * <p>
 * Deliberately NOT proxied through ProxyController/gRPC like every other
 * route: an SSE connection is long-lived and this data doesn't belong to
 * any single downstream service (it's the fan-in of all of them), so it's
 * served directly from the Gateway, which is also the only place JWT
 * verification already happens without a round-trip.
 */
@RestController
@RequestMapping("/api/live")
@RequiredArgsConstructor
@Tag(name = "Live Updates", description = "Admin-only: SSE stream of every platform domain event")
public class LiveUpdatesController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to a live stream of every domain event (deployments, jobs, flags, monitoring) plus a periodic heartbeat")
    public SseEmitter stream(HttpServletRequest request) {
        requireAdmin(request);
        return registry.register();
    }

    @SuppressWarnings("unchecked")
    private void requireAdmin(HttpServletRequest request) {
        Object rolesAttr = request.getAttribute(JwtAuthFilter.USER_ROLES_ATTRIBUTE);
        List<String> roles = rolesAttr instanceof List<?> ? (List<String>) rolesAttr : List.of();
        if (!roles.contains("ROLE_ADMIN")) {
            throw new ApiException("Live updates are admin-only", HttpStatus.FORBIDDEN);
        }
    }
}
